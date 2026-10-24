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
    def test_oversized_serial_reply_is_bounded(self):
        class Fake:
            def write(self,data):pass
            def read(self,n):return b'A'
        adapter=SerialAdapter.__new__(SerialAdapter);adapter.serial=Fake();adapter.sequence=0
        with self.assertRaisesRegex(ReaderError,'Oversized'):adapter.request('STATUS')
    def test_remote_server_urls_are_rejected_before_network_use(self):
        for url in ['https://localhost:8787','http://example.com','http://localhost@evil.test','http://localhost:8787/path']:
            with self.assertRaises(ReaderError):LocalMerchant(url)

if __name__=='__main__':unittest.main()
