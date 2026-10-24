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
        time.sleep(2)  # Some ESP32 USB UART bridges reset when the port opens.
        self.serial.reset_input_buffer()
        self.sequence = 0
    def request(self, op, data=''):
        self.sequence += 1
        ident = self.sequence
        self.serial.write(f'{op} {ident}{" " + data if data else ""}\n'.encode('ascii'))
        deadline = time.monotonic() + 4
        buffer = bytearray()
        while time.monotonic() < deadline:
            byte = self.serial.read(1)
            if not byte:
                continue
            if byte == b'\n':
                try:
                    event = json.loads(buffer)
                except (ValueError, UnicodeDecodeError):
                    buffer.clear()
                    continue  # Ignore bounded boot-ROM text, never print raw lines.
                buffer.clear()
                if not isinstance(event,dict) or event.get('id') != ident:
                    continue
                if event.get('event') == 'error':
                    raise ReaderError('Reader command failed: ' + str(event.get('code', 'unknown'))[:50])
                return event
            buffer.extend(byte)
            if len(buffer) > 2048:
                raise ReaderError('Oversized serial reply. Reconnect the reader.')
        raise ReaderError('Reader did not reply. Check USB, firmware and PN532.')
    def status(self):
        result = self.request('STATUS')
        if result.get('firmware') != 'tap-reader-0.5' or result.get('event') != 'status':
            raise ReaderError('Unexpected firmware. Flash tap-reader-0.5.')
        return result.get('pn532') is True
    def poll(self):
        return self.request('POLL').get('found') is True
    def exchange(self, command):
        result = self.request('APDU', command.hex().upper())
        try:
            if result.get('event') != 'apdu' or not isinstance(result.get('response'),str) or len(result['response']) > 408:
                raise ValueError()
            response = bytes.fromhex(result['response'])
        except (ValueError, KeyError):
            raise ReaderError('Malformed APDU reply.') from None
        return response
    def close(self):
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
    metadata = response_data(adapter.exchange(select_command))
    if len(metadata) != 49 or metadata[0] != 1:
        raise ReaderError('Unsupported phone metadata.')
    session = metadata[1:9]
    length, chunk_size, count = struct.unpack('>IHH', metadata[9:17])
    if not 14 <= length <= 65536 or chunk_size != 192 or count != (length + 191)//192:
        raise ReaderError('Invalid phone packet bounds.')
    expected_hash = metadata[17:]
    packet = bytearray()
    retries = 0
    for index in range(count):
        command = b'\x80\x10\x00\x00\x0a' + session + struct.pack('>H',index)
        for attempt in range(3):
            try:
                data = response_data(adapter.exchange(command))
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
        state('receiving')
    packet = bytes(packet)
    if len(packet) != length or hashlib.sha256(packet).digest() != expected_hash:
        raise ReaderError('Packet integrity check failed.')
    publish(packet, session, adapter.simulated)  # Node TAG1 decoder can reject here; no Finish on rejection.
    state('verified')
    try:
        response_data(adapter.exchange(b'\x80\x20\x00\x00\x28' + session + expected_hash))
    except ReaderError:
        state('ack_lost')
        return {'bytes':length,'chunks':count,'retries':retries,'acknowledged':False,'simulated':adapter.simulated}
    state('complete')
    return {'bytes':length,'chunks':count,'retries':retries,'acknowledged':True,'simulated':adapter.simulated}

def two_tap(adapter, merchant):
    context=merchant.request('/api/reader/checkout').get('checkout')
    if not context:
        raise ReaderError('No two-tap order is ready. Choose a tip or start an order on the merchant.')
    binding=bytes.fromhex(context['id'].replace('-','')+context['nonce'])
    payload=binding+struct.pack('>IB',context['amount'],context['phase'])
    response_data(adapter.exchange(b'\x80\x30\x00\x00\x25'+payload))
    if context['phase']==1:
        merchant.request('/api/reader/accepted',{'id':context['id'],'nonce':context['nonce']})
        response_data(adapter.exchange(b'\x80\x32\x00\x00\x01\x01'))
        return {'first_tap':True,'acknowledged':True,'simulated':adapter.simulated}
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
                        merchant.state("complete" if result["acknowledged"] else "ack_lost",False)
                        time.sleep(.5)
                except ReaderError as error:
                    merchant.state('transfer_error',adapter.simulated)
                    print(str(error))
                    if adapter.simulated: raise
                    time.sleep(1)
            time.sleep(.2)
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
