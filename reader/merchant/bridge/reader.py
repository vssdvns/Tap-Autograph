"""USB APDU bridge. Plaintext prototype, no card credentials. Python 3.10+."""
import argparse
import base64
import hashlib
import json
import struct
import time
import urllib.parse
import urllib.request
import urllib.error
from pathlib import Path

SELECT = bytes.fromhex('00A4040008F05441504155540100')

class ReaderError(Exception):
    pass

class LocalMerchant:
    def __init__(self, url):
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password or parsed.path not in ('', '/') or parsed.query or parsed.fragment:
            raise ReaderError('Use a localhost HTTP merchant URL.')
        self.url = url.rstrip('/')
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.token = self.request('/api/reader/config')['token']
    def request(self, path, body=None):
        headers = {}
        if hasattr(self, 'token'):
            headers['Authorization'] = 'Bearer ' + self.token
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(self.url + path, data=data, headers=headers)
        try:
            with self.opener.open(req, timeout=5) as response:
                return json.load(response)
        except urllib.error.URLError as error:
            raise ReaderError("Merchant unavailable or checkout changed. Check the laptop, then retry.") from None
    def state(self, code, simulated=False):
        self.request('/api/reader/event', {'type':'state', 'code':code, 'simulated':simulated})
    def packet(self, packet, session, simulated, checkout=None):
        self.request('/api/reader/event', {'type':'packet','session':session.hex(), 'sha256':hashlib.sha256(packet).hexdigest(), 'packet':base64.b64encode(packet).decode(), 'simulated':simulated, **({'checkoutId':checkout['id'],'nonce':checkout['nonce']} if checkout else {})})

class SerialAdapter:
    simulated = False
    def __init__(self, port):
        import serial
        self.serial = serial.Serial(port, 115200, timeout=.1, write_timeout=1)
        self.serial.dtr = False
        self.serial.rts = False
        time.sleep(3.5)  # USB reset/startup and high-speed idle fallback to 115200.
        self.serial.reset_input_buffer()
        self.sequence = 0
    def request(self, op, data=''):
        self.sequence += 1
        ident = self.sequence
        self.serial.write(f'{op} {ident}{" " + data if data else ""}\n'.encode('ascii'))
        deadline = time.monotonic() + (30 if op == 'STATUS' else 4)
        buffer = bytearray()
        while time.monotonic() < deadline:
            available = min(2048, max(1, getattr(self.serial,'in_waiting',0)))
            incoming = self.serial.read(available)
            for byte in incoming:
                if byte == 10:
                    try:
                        event = json.loads(buffer)
                    except (ValueError, UnicodeDecodeError):
                        buffer.clear()
                        continue
                    buffer.clear()
                    if not isinstance(event,dict) or event.get('id') != ident:
                        continue
                    if event.get('event') == 'error':
                        raise ReaderError('Reader command failed: ' + str(event.get('code', 'unknown'))[:50])
                    return event
                buffer.append(byte)
                if len(buffer) > 2048:
                    raise ReaderError('Oversized serial reply. Reconnect the reader.')
        raise ReaderError('Reader did not reply. Check USB, firmware and PN532.')
    def status(self):
        result = self.request('STATUS')
        if result.get('firmware') != 'tap-reader-0.5' or result.get('event') != 'status':
            raise ReaderError('Unexpected firmware. Flash tap-reader-0.5.')
        self.performance = result.get('performance') == 1
        self.transport = result.get('transport',0)
        return result.get('pn532') is True
    def tune(self, poll_ms):
        if not getattr(self, 'performance', False):
            raise ReaderError('Flash the performance reader firmware before selecting a polling profile.')
        result = self.request('TUNE', str(poll_ms))
        if result.get('event') != 'tuned' or result.get('poll_ms') != poll_ms:
            raise ReaderError('Reader did not accept the polling profile.')
        self.poll_ms = poll_ms
    def fast_usb(self):
        if getattr(self, 'transport', 0) != 2:
            raise ReaderError('Flash the transport-2 firmware before enabling fast USB.')
        result = self.request('BAUD', '460800')
        if result.get('event') != 'baud' or result.get('serial_baud') != 460800:
            raise ReaderError('Reader did not accept fast USB.')
        self.serial.baudrate = 460800
        time.sleep(.075)
        result = self.request('STATUS')
        if result.get('event') != 'status' or result.get('transport') != 2 or result.get('serial_baud') != 460800:
            raise ReaderError('Fast USB handshake failed. Reconnect and use the baseline launcher.')
        self.binary_reply = True
    def poll(self):
        return self.request('POLL').get('found') is True
    def exchange(self, command):
        started = time.monotonic()
        result = self.request('APDUB' if getattr(self,'binary_reply',False) else 'APDU', command.hex().upper())
        elapsed = time.monotonic() - started
        firmware_us = result.get('exchange_us')
        self.last_exchange_metrics = {'roundtrip_seconds': elapsed, 'reader_seconds': firmware_us / 1_000_000 if type(firmware_us) is int and 0 <= firmware_us <= 4_000_000 else None}
        try:
            if result.get('event') != 'apdu': raise ValueError()
            if getattr(self,'binary_reply',False):
                encoded=result.get('response_b64')
                if not isinstance(encoded,str) or len(encoded)>272: raise ValueError()
                response=base64.b64decode(encoded,validate=True)
                if base64.b64encode(response).decode()!=encoded: raise ValueError()
            else:
                if not isinstance(result.get('response'),str) or len(result['response'])>408: raise ValueError()
                response=bytes.fromhex(result['response'])
            if not 2 <= len(response) <= 204: raise ValueError()
        except (ValueError, KeyError):
            raise ReaderError('Malformed APDU reply.') from None
        return response
    def close(self):
        if getattr(self.serial,'baudrate',115200)!=115200:
            try:
                self.request('BAUD','115200')
                time.sleep(.075)
            except (ReaderError, OSError):
                pass  # Firmware also returns to 115200 after three idle seconds.
        self.serial.close()

class ReplayAdapter:
    """Hardware-free, explicitly simulated Kotlin fixture transport."""
    simulated = True
    def __init__(self, filename, drop_once=True):
        transcript = json.loads(Path(filename).read_text())
        if transcript.get('protocol') != 'TAP1' or transcript.get('simulated') is not True:
            raise ReaderError('Expected a TAP1 synthetic transcript.')
        self.responses = {bytes.fromhex(e['command']):bytes.fromhex(e['response']) for e in transcript['exchanges']}
        self.drop_once = drop_once
    def status(self): return True
    def poll(self): return True
    def exchange(self, command):
        if self.drop_once and command[:2] == b'\x80\x10':
            self.drop_once = False
            raise ReaderError('Injected simulated link interruption')
        if command not in self.responses:
            raise ReaderError('Fixture lacks this command.')
        return self.responses[command]
    def close(self): pass

def response_data(response):
    if len(response) < 2 or len(response) > 204:
        raise ReaderError('Invalid APDU response size.')
    if response[-2:] != b'\x90\x00':
        raise ReaderError('Phone declined. Start the two-tap demo (or enable lab sharing), keep the app open and unlocked.')
    return response[:-2]

def read_transfer(adapter, publish, state=lambda code:None, select_command=SELECT):
    """publish must decode/accept on laptop before we send the phone Finish."""
    started = time.monotonic()
    metadata = response_data(adapter.exchange(select_command))
    metadata_done = time.monotonic()
    chunk_metrics = []
    if len(metadata) != 49 or metadata[0] != 1:
        raise ReaderError('Unsupported phone metadata.')
    session = metadata[1:9]
    length, chunk_size, count = struct.unpack('>IHH', metadata[9:17])
    if not 14 <= length <= 65536 or chunk_size != 192 or count != (length + 191)//192:
        raise ReaderError('Invalid phone packet bounds.')
    expected_hash = metadata[17:]
    packet = bytearray()
    retries = 0
    last_update = started - 1
    for index in range(count):
        command = b'\x80\x10\x00\x00\x0a' + session + struct.pack('>H',index)
        for attempt in range(3):
            try:
                data = response_data(adapter.exchange(command))
                metric = getattr(adapter, 'last_exchange_metrics', None)
                if metric is not None: chunk_metrics.append(metric.copy())
                expected_size = min(192,length-index*192)
                if len(data) != 10 + expected_size or data[:8] != session or data[8:10] != struct.pack('>H',index):
                    raise ReaderError('Invalid or stale chunk.')
                packet.extend(data[10:])
                break
            except ReaderError:
                if attempt == 2: raise
                retries += 1
                state('retrying')
                if not adapter.poll():
                    time.sleep(.3)
                    if not adapter.poll(): raise ReaderError('Phone removed. Re-enable sharing and try again.')
                if response_data(adapter.exchange(select_command)) != metadata:
                    raise ReaderError('Phone grant changed. Start a new transfer.')
        # UI progress must not add one HTTP round trip to every 192-byte NFC chunk.
        tick = time.monotonic()
        if index == count - 1 or tick - last_update >= .25:
            state('receiving')
            last_update = tick
    packet = bytes(packet)
    if len(packet) != length or hashlib.sha256(packet).digest() != expected_hash:
        raise ReaderError('Packet integrity check failed.')
    read_done = time.monotonic()
    publish(packet, session, adapter.simulated)  # Node TAG1 decoder can reject here; no Finish on rejection.
    published = time.monotonic()
    state('verified')
    metrics = {'packet_format': packet[:4].decode('ascii', errors='replace')}
    if isinstance(adapter,SerialAdapter):
        metrics['serial_baud']=getattr(adapter.serial,'baudrate',115200)
        metrics['usb_encoding']='base64' if getattr(adapter,'binary_reply',False) else 'hex'
    if getattr(adapter, 'poll_ms', None) is not None: metrics['poll_ms'] = adapter.poll_ms
    if chunk_metrics:
        metrics['chunk_roundtrip_seconds'] = round(sum(m['roundtrip_seconds'] for m in chunk_metrics), 4)
        measured = [m for m in chunk_metrics if m['reader_seconds'] is not None]
        if measured:
            metrics['reader_exchange_seconds'] = round(sum(m['reader_seconds'] for m in measured), 4)
            metrics['usb_host_seconds'] = round(sum(max(0, m['roundtrip_seconds'] - m['reader_seconds']) for m in measured), 4)
            metrics['measured_chunks'] = len(measured)
    try:
        response_data(adapter.exchange(b'\x80\x20\x00\x00\x28' + session + expected_hash))
    except ReaderError:
        state('ack_lost')
        return {'bytes':length,'chunks':count,'seconds':round(time.monotonic()-started,3),'metadata_seconds':round(metadata_done-started,3),'read_seconds':round(read_done-metadata_done,3),'publish_seconds':round(published-read_done,3),'retries':retries,'acknowledged':False,'simulated':adapter.simulated, **metrics}
    state('complete')
    return {'bytes':length,'chunks':count,'seconds':round(time.monotonic()-started,3),'metadata_seconds':round(metadata_done-started,3),'read_seconds':round(read_done-metadata_done,3),'publish_seconds':round(published-read_done,3),'retries':retries,'acknowledged':True,'simulated':adapter.simulated, **metrics}

def two_tap(adapter, merchant):
    context=merchant.request('/api/reader/checkout').get('checkout')
    if not context:
        raise ReaderError('No two-tap order is ready. Choose a tip or start an order on the merchant.')
    if context.get('tipPending'):
        context=merchant.request('/api/reader/confirm-tip',{'id':context['id'],'nonce':context['nonce'],'tipRevision':context['tipRevision']})['checkout']
    binding=bytes.fromhex(context['id'].replace('-','')+context['nonce'])
    payload=binding+struct.pack('>IB',context['amount'],context['phase'])
    offer_result=response_data(adapter.exchange(b'\x80\x30\x00\x00\x25'+payload))
    if context['phase']==1:
        merchant.request('/api/reader/accepted',{'id':context['id'],'nonce':context['nonce'], **({'card':offer_result[1]} if len(offer_result)==2 and offer_result[1] in (0,1) else {})})
        response_data(adapter.exchange(b'\x80\x32\x00\x00\x01\x01'))
        return {'first_tap':True,'acknowledged':True,'simulated':adapter.simulated}
    if offer_result[:1]==b'\x04':
        return read_transfer(adapter,lambda packet,session,simulated:merchant.packet(packet,session,simulated,context),lambda code:merchant.state(code,adapter.simulated),select_command=b'\x80\x33\x00\x00\x00')
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        status=response_data(adapter.exchange(b'\x80\x31\x00\x00\x00'))
        if status==b'\x04':
            return read_transfer(adapter,lambda packet,session,simulated:merchant.packet(packet,session,simulated,context),lambda code:merchant.state(code,adapter.simulated),select_command=b'\x80\x33\x00\x00\x00')
        if status==b'\x06':
            merchant.request('/api/reader/skip-checkout',{'id':context['id'],'nonce':context['nonce']})
            return {'skipped':True,'acknowledged':True,'simulated':adapter.simulated}
        if status!=b'\x03':raise ReaderError('Phone demo expired or changed. Restart the checkout.')
        time.sleep(.2)
    raise ReaderError('Review timed out. Remove phone, then tap again to review and share.')

def main():
    parser=argparse.ArgumentParser(description='Tap Autograph USB bridge (plaintext prototype)')
    parser.add_argument('--port',help='USB serial port, for example COM5')
    parser.add_argument('--server',default='http://127.0.0.1:8787')
    parser.add_argument('--simulate',metavar='JSON',help='Replay a synthetic reader transcript without hardware')
    parser.add_argument('--fast-usb',action='store_true',help='Negotiate 460800 baud and base64 replies (transport-2 firmware)')
    parser.add_argument('--poll-ms',type=int,choices=(1,10),help='Performance firmware polling profile; no baud/wiring change')
    parser.add_argument('--list',action='store_true',help='List USB serial ports; opens none')
    args=parser.parse_args()
    if args.list:
        from serial.tools import list_ports
        for port in list_ports.comports(): print(f'{port.device}: {port.description}')
        return
    if not args.simulate and not args.port:
        parser.error('Choose --port COMx or --simulate fixture.json.')
    merchant=LocalMerchant(args.server)
    adapter=None
    try:
        adapter=ReplayAdapter(args.simulate) if args.simulate else SerialAdapter(args.port)
        merchant.state('connected',adapter.simulated)
        if not adapter.status():
            merchant.state('pn532_missing',adapter.simulated)
            raise ReaderError('ESP32 replied, but PN532 was not detected. Check mode, power and wiring, then reset.')
        if args.fast_usb:
            if args.simulate: raise ReaderError('Fast USB requires physical reader hardware.')
            adapter.fast_usb()
            print('Fast USB active: 460800 baud, base64 replies.')
        if args.poll_ms is not None:
            if args.simulate: raise ReaderError('Polling profiles require physical reader hardware.')
            adapter.tune(args.poll_ms)
            print(f'Reader performance profile: {args.poll_ms} ms readiness polling; buffered USB replies.')
        merchant.state('ready',adapter.simulated)
        print('SIMULATED READER' if adapter.simulated else 'USB reader ready. Open Two-tap NFC checkout or NFC payload lab; keep phone unlocked.')
        last_heartbeat = 0.0
        while True:
            if time.monotonic()-last_heartbeat>2:
                merchant.state("ready",adapter.simulated)
                last_heartbeat=time.monotonic()
            if adapter.poll():
                merchant.state('phone_found',adapter.simulated)
                try:
                    metadata=response_data(adapter.exchange(SELECT))
                    result=two_tap(adapter,merchant) if metadata[:1]==b'\x02' else read_transfer(adapter,merchant.packet,lambda code:merchant.state(code,adapter.simulated))
                    print(json.dumps(result))
                    if adapter.simulated: break
                    # One transfer per consent grant; don't repeatedly display a held phone.
                    while adapter.poll():
                        if result.get("first_tap"):
                            # V2 supports staying in the field while the merchant selects a tip.
                            next_order=merchant.request('/api/reader/checkout').get('checkout')
                            if next_order and next_order.get('phase')==2:
                                response_data(adapter.exchange(SELECT))
                                result=two_tap(adapter,merchant)
                                print(json.dumps(result))
                        else:
                            merchant.state("complete" if result["acknowledged"] else "ack_lost",False)
                        time.sleep(.08 if result.get("first_tap") else .5)
                except ReaderError as error:
                    merchant.state('transfer_error',adapter.simulated)
                    print(str(error))
                    if adapter.simulated: raise
                    time.sleep(1)
            time.sleep(.05)
    except KeyboardInterrupt:
        print('Bridge stopped.')
    finally:
        if adapter: adapter.close()
        try: merchant.state('disconnected',bool(args.simulate))
        except Exception: pass

if __name__ == '__main__':
    try: main()
    except Exception as error:
        # Do not print file contents, APDUs or the handwriting packet.
        print('Bridge could not continue: ' + str(error)[:180])
        raise SystemExit(1)
