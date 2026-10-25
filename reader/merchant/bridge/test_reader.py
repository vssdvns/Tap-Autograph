import unittest
import sys
import hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from reader import ReplayAdapter, SerialAdapter, ReaderError, read_transfer, SELECT, LocalMerchant
FIXTURE=Path(__file__).resolve().parents[1]/'nfc/fixtures/large.json'

class ReaderTests(unittest.TestCase):
    def test_large_packet_retries_then_publishes_before_finish(self):
        adapter=ReplayAdapter(FIXTURE)
        events=[];published=[]
        exchange=adapter.exchange
        def traced(command):
            if command[:2]==b'\x80\x20': self.assertTrue(published)
            return exchange(command)
        adapter.exchange=traced
        result=read_transfer(adapter,lambda *args:published.append(args),events.append)
        self.assertEqual(result['chunks'],103)
        self.assertEqual(result['retries'],1)
        self.assertTrue(result['simulated']);self.assertTrue(result['acknowledged'])
        self.assertEqual(published[0][0],FIXTURE.with_suffix('.tai').read_bytes())
        self.assertIn('retrying',events);self.assertEqual(events[-1],'complete')
    def test_progress_is_throttled_and_transfer_duration_reported(self):
        from unittest.mock import patch
        events=[]
        with patch('reader.time.monotonic',return_value=1):
            result=read_transfer(ReplayAdapter(FIXTURE,False),lambda *args:None,events.append)
        self.assertEqual(2,events.count('receiving'))
        self.assertEqual(0,result['seconds'])
        self.assertTrue(result['acknowledged'])
    def test_kotlin_bitmap_packet_uses_existing_reader_framing(self):
        fixture=FIXTURE.parent/'raster-reader.json';published=[]
        result=read_transfer(ReplayAdapter(fixture),lambda *args:published.append(args))
        self.assertEqual(published[0][0],(FIXTURE.parent/'raster.tai').read_bytes())
        self.assertEqual(result['chunks'],1);self.assertTrue(result['acknowledged'])
    def test_rejected_laptop_packet_is_never_acknowledged(self):
        adapter=ReplayAdapter(FIXTURE,False);commands=[];exchange=adapter.exchange
        def traced(cmd):commands.append(cmd);return exchange(cmd)
        adapter.exchange=traced
        def reject(*args): raise ReaderError('Node decoder rejected packet')
        with self.assertRaises(ReaderError):read_transfer(adapter,reject)
        self.assertFalse(any(cmd[:2]==b'\x80\x20' for cmd in commands))
    def test_changed_grant_after_link_interruption_is_rejected(self):
        adapter=ReplayAdapter(FIXTURE);original=adapter.responses[SELECT]
        adapter.responses[SELECT]=original
        exchange=adapter.exchange;selects=0
        def changed(cmd):
            nonlocal selects
            if cmd==SELECT:
                selects+=1
                if selects>1:return original[:1]+bytes([original[1]^1])+original[2:]
            return exchange(cmd)
        adapter.exchange=changed
        with self.assertRaisesRegex(ReaderError,'grant changed'):read_transfer(adapter,lambda *args:self.fail('Published stale packet'))
    def test_hash_corruption_never_publishes(self):
        adapter=ReplayAdapter(FIXTURE,False)
        for command,response in list(adapter.responses.items()):
            if command[:2]==b'\x80\x10':adapter.responses[command]=response[:10]+bytes([response[10]^1])+response[11:];break
        with self.assertRaisesRegex(ReaderError,'integrity'):read_transfer(adapter,lambda *args:self.fail('Published corrupted packet'))
    def test_lost_finish_ack_does_not_claim_phone_completion(self):
        adapter=ReplayAdapter(FIXTURE,False);exchange=adapter.exchange;events=[]
        def drop(cmd):
            if cmd[:2]==b'\x80\x20':raise ReaderError('lost')
            return exchange(cmd)
        adapter.exchange=drop
        result=read_transfer(adapter,lambda *args:None,events.append)
        self.assertFalse(result['acknowledged']);self.assertEqual(events[-1],'ack_lost')
    def test_serial_parser_ignores_boot_text_and_wrong_request_ids(self):
        class Fake:
            def __init__(self):self.buffer=bytearray(b'boot garbage\n{"id":99,"event":"status"}\n{"id":1,"event":"status","firmware":"tap-reader-0.5","pn532":true}\n');self.written=None
            def write(self,data):self.written=data
            def read(self,n):data=bytes(self.buffer[:n]);del self.buffer[:n];return data
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.serial=Fake();adapter.sequence=0
        self.assertTrue(adapter.status());self.assertEqual(adapter.serial.written,b'STATUS 1\n')
    def test_serial_parser_reads_available_bytes_in_one_batch(self):
        class Fake:
            def __init__(self):self.buffer=bytearray(b'boot\n{"id":99}\n{"id":1,"event":"apdu","response":"9000"}\n');self.reads=0
            @property
            def in_waiting(self):return len(self.buffer)
            def write(self,data):pass
            def read(self,n):self.reads+=1;value=bytes(self.buffer[:n]);del self.buffer[:n];return value
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.serial=Fake();adapter.sequence=0
        self.assertEqual(adapter.exchange(b'12345'),b'\x90\x00');self.assertEqual(adapter.serial.reads,1)
    def test_oversized_serial_reply_is_bounded(self):
        class Fake:
            def write(self,data):pass
            def read(self,n):return b'A'
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.serial=Fake();adapter.sequence=0
        with self.assertRaisesRegex(ReaderError,'Oversized'):adapter.request('STATUS')
    def test_performance_profile_requires_capability_and_matching_ack(self):
        adapter=SerialAdapter.__new__(SerialAdapter)
        with self.assertRaisesRegex(ReaderError,'Flash'):adapter.tune(1)
        adapter.performance=True
        calls=[]
        def request(op,data):calls.append((op,data));return {'event':'tuned','poll_ms':1}
        adapter.request=request;adapter.tune(1)
        self.assertEqual(calls,[('TUNE','1')])
        with self.assertRaisesRegex(ReaderError,'accept'):adapter.tune(10)
    def test_transfer_reports_separate_reader_and_usb_timings(self):
        adapter=ReplayAdapter(FIXTURE.parent/'raster-reader.json',False)
        exchange=adapter.exchange
        def timed(cmd):
            result=exchange(cmd)
            adapter.last_exchange_metrics={'roundtrip_seconds':.16,'reader_seconds':.11}
            return result
        adapter.exchange=timed
        result=read_transfer(adapter,lambda *args:None)
        self.assertEqual(result['measured_chunks'],1)
        self.assertEqual(result['chunk_roundtrip_seconds'],.16)
        self.assertEqual(result['reader_exchange_seconds'],.11)
        self.assertEqual(result['usb_host_seconds'],.05)
    def test_legacy_firmware_metrics_remain_optional(self):
        adapter=SerialAdapter.__new__(SerialAdapter)
        adapter.request=lambda *args:{'event':'apdu','response':'9000'}
        self.assertEqual(adapter.exchange(b'12345'),b'\x90\x00')
        self.assertIsNone(adapter.last_exchange_metrics['reader_seconds'])
        adapter.request=lambda *args:{'event':'apdu','response':'9000','exchange_us':-1}
        adapter.exchange(b'12345')
        self.assertIsNone(adapter.last_exchange_metrics['reader_seconds'])
    def test_fast_usb_handshake_and_shutdown_restore_legacy_speed(self):
        from unittest.mock import patch
        class Port:
            baudrate=115200
            def close(self):self.closed=True
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.serial=Port();adapter.transport=2
        calls=[]
        def request(op,data=''):
            calls.append((op,data,adapter.serial.baudrate))
            return {'event':'baud','serial_baud':int(data)} if op=='BAUD' else {'event':'status','transport':2,'serial_baud':adapter.serial.baudrate}
        adapter.request=request
        with patch('reader.time.sleep'):
            adapter.fast_usb();self.assertTrue(adapter.binary_reply);adapter.close()
        self.assertEqual(calls,[('BAUD','460800',115200),('STATUS','',460800),('BAUD','115200',460800)])
        self.assertTrue(adapter.serial.closed)
    def test_fast_usb_requires_capability_and_verifies_new_speed(self):
        from unittest.mock import patch
        adapter=SerialAdapter.__new__(SerialAdapter)
        with self.assertRaisesRegex(ReaderError,'transport-2'):adapter.fast_usb()
        class Port:baudrate=115200
        adapter.serial=Port();adapter.transport=2
        adapter.request=lambda op,data='':{'event':'baud','serial_baud':460800} if op=='BAUD' else {'event':'status','serial_baud':115200}
        with patch('reader.time.sleep'),self.assertRaisesRegex(ReaderError,'handshake'):adapter.fast_usb()
    def test_base64_reply_is_exact_bounded_and_canonical(self):
        import base64
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.binary_reply=True
        response=bytes(range(204));calls=[]
        def request(op,data):calls.append(op);return {'event':'apdu','response_b64':base64.b64encode(response).decode(),'exchange_us':1000}
        adapter.request=request
        self.assertEqual(adapter.exchange(b'12345'),response);self.assertEqual(calls,['APDUB'])
        for bad in ['!!!!','AA==','A'*276,'Zh==']:
            adapter.request=lambda *args:{'event':'apdu','response_b64':bad}
            with self.assertRaisesRegex(ReaderError,'Malformed'):adapter.exchange(b'12345')
    def test_kotlin_column_fixture_preserves_transfer_integrity(self):
        fixture=FIXTURE.parent/'column-reader.json';published=[]
        result=read_transfer(ReplayAdapter(fixture),lambda *args:published.append(args))
        self.assertEqual(published[0][0],(FIXTURE.parent/'column.tai').read_bytes())
        self.assertEqual(result['packet_format'],'TAG3')
        self.assertTrue(result['acknowledged']);self.assertTrue(result['simulated'])
    def test_spi_mode_requires_capability_and_matching_ack(self):
        adapter=SerialAdapter.__new__(SerialAdapter)
        with self.assertRaisesRegex(ReaderError,'SPI performance'):adapter.configure_spi(True)
        adapter.spi_bulk_supported=True;calls=[]
        def request(op,data):calls.append((op,data));return {'event':'spi','bulk_read':data=='1'}
        adapter.request=request;adapter.configure_spi(True);self.assertTrue(adapter.bulk_read)
        adapter.configure_spi(False);self.assertFalse(adapter.bulk_read)
        self.assertEqual(calls,[('SPI','1'),('SPI','0')])
        adapter.request=lambda *args:{'event':'spi','bulk_read':1}
        with self.assertRaisesRegex(ReaderError,'accept'):adapter.configure_spi(True)
    def test_exchange_phase_metrics_are_bounded_and_summed(self):
        adapter=SerialAdapter.__new__(SerialAdapter)
        response={'event':'apdu','response':'9000','exchange_us':100000,'write_us':1000,'ack_us':2000,'wait_us':90000,'read_us':3000}
        adapter.request=lambda *args:response
        adapter.exchange(b'12345');self.assertEqual(adapter.last_exchange_metrics['phases']['read'],.003)
        response['read_us']=-1;adapter.exchange(b'12345');self.assertNotIn('phases',adapter.last_exchange_metrics)
        response['read_us']=4000000;adapter.exchange(b'12345');self.assertNotIn('phases',adapter.last_exchange_metrics)
    def test_kotlin_predictive_fixture_preserves_transfer_integrity(self):
        fixture=FIXTURE.parent/'predictive-reader.json';published=[]
        result=read_transfer(ReplayAdapter(fixture),lambda *args:published.append(args))
        self.assertEqual(published[0][0],(FIXTURE.parent/'predictive.tai').read_bytes())
        self.assertEqual(result['packet_format'],'TAG4');self.assertTrue(result['acknowledged'])
    def test_phase_totals_cover_only_measured_chunk_replies(self):
        adapter=ReplayAdapter(FIXTURE.parent/'predictive-reader.json',False);exchange=adapter.exchange
        def timed(cmd):
            value=exchange(cmd)
            adapter.last_exchange_metrics={'roundtrip_seconds':.16,'reader_seconds':.11,'phases':{'write':.001,'ack':.002,'wait':.1,'read':.007}}
            return value
        adapter.exchange=timed
        result=read_transfer(adapter,lambda *args:None)
        self.assertEqual(result['phase_measured_chunks'],result['chunks'])
        self.assertEqual(result['pn532_read_seconds'],round(.007*result['chunks'],4))
        self.assertEqual(result['pn532_wait_seconds'],round(.1*result['chunks'],4))
    def test_remote_server_urls_are_rejected_before_network_use(self):
        for url in ['https://localhost:8787','http://example.com','http://localhost@evil.test','http://localhost:8787/path']:
            with self.assertRaises(ReaderError):LocalMerchant(url)

if __name__=='__main__':unittest.main()
