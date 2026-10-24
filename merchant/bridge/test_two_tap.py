import unittest
import sys
import struct
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from reader import two_tap, SELECT, ReaderError

class Phone:
    simulated=True
    def __init__(self): self.commands=[]; self.reviewed=False
    def exchange(self,cmd):
        self.commands.append(cmd)
        if cmd[1]==0x30:return b'\x01\x90\x00'
        if cmd[1]==0x32:return b'\x90\x00'
        if cmd[1]==0x31:return b'\x06\x90\x00'
        raise ReaderError('unexpected command')
class Merchant:
    def __init__(self,phase=1):self.phase=phase;self.calls=[]
    def request(self,path,body=None):
        self.calls.append((path,body))
        if path.endswith('/checkout'):return {'checkout':{'id':'00010203-0405-0607-0809-0a0b0c0d0e0f','nonce':'101112131415161718191a1b1c1d1e1f','amount':1200,'phase':self.phase}}
        return {'accepted':True}
class TwoTapTests(unittest.TestCase):
    def test_first_tap_is_merchant_accepted_before_phone_ack(self):
        phone=Phone();merchant=Merchant();result=two_tap(phone,merchant)
        self.assertTrue(result['first_tap']);self.assertEqual(phone.commands[-1],bytes.fromhex('803200000101'))
        self.assertEqual(merchant.calls[-1][0],'/api/reader/accepted')
        offer=phone.commands[0];self.assertEqual(len(offer),42);self.assertEqual(offer[5:37],bytes(range(32)));self.assertEqual(struct.unpack('>IB',offer[37:]),(1200,1))
    def test_automatic_offer_propagates_selected_demo_card(self):
        phone=Phone();original=phone.exchange
        phone.exchange=lambda cmd: b'\x01\x01\x90\x00' if cmd[1]==0x30 else original(cmd)
        merchant=Merchant();two_tap(phone,merchant)
        self.assertEqual(merchant.calls[-1][1]['card'],1)
    def test_skip_never_reads_or_publishes_ink(self):
        phone=Phone();merchant=Merchant(2);result=two_tap(phone,merchant)
        self.assertTrue(result['skipped']);self.assertEqual(merchant.calls[-1][0],'/api/reader/skip-checkout')
        self.assertFalse(any(c[1] in [0x10,0x20,0x33] for c in phone.commands))
    def test_failed_first_acceptance_is_not_acknowledged_to_phone(self):
        phone=Phone();merchant=Merchant();original=merchant.request
        def request(path,body=None):
            if path.endswith('/accepted'):raise ReaderError('cancelled')
            return original(path,body)
        merchant.request=request
        with self.assertRaises(ReaderError):two_tap(phone,merchant)
        self.assertFalse(any(c[1]==0x32 for c in phone.commands))

class KotlinInteropTests(unittest.TestCase):
    def test_actual_kotlin_two_tap_transcript_reaches_finish_after_bound_publish(self):
        self.verify_fixture('two-tap.json')
    def test_automatic_kotlin_transcript_needs_no_approve_button(self):
        self.verify_fixture('automatic-two-tap.json')
    def verify_fixture(self,filename):
        import json,base64
        fixture=json.loads((Path(__file__).resolve().parents[1]/'nfc/fixtures'/filename).read_text())
        steps=list(fixture['exchanges']);published=[]
        class Adapter:
            simulated=True
            def exchange(self,cmd):
                step=steps.pop(0)
                if bytes.fromhex(step['command'])!=cmd:raise AssertionError('command differs from Kotlin')
                if cmd[1]==0x20:assert published
                return bytes.fromhex(step['response'])
        class BoundMerchant(Merchant):
            def request(self,path,body=None):
                result=super().request(path,body)
                if path.endswith('/checkout'):result['checkout']['amount']=1000 if self.phase==1 else 1200
                return result
            def packet(self,packet,session,simulated,checkout):
                assert packet==base64.b64decode(fixture['packet'])
                assert checkout['nonce']=='101112131415161718191a1b1c1d1e1f'
                assert simulated is True
                published.append(packet)
            def state(self,*args):pass
        adapter=Adapter();merchant=BoundMerchant()
        adapter.exchange(SELECT);self.assertTrue(two_tap(adapter,merchant)['first_tap'])
        merchant.phase=2;adapter.exchange(SELECT);result=two_tap(adapter,merchant)
        self.assertTrue(result['acknowledged']);self.assertTrue(result['simulated']);self.assertEqual(len(published),1);self.assertEqual(steps,[])
