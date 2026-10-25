"""Host test of the actual command handler with mocked hardware; requires g++."""
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as directory:
    p = Path(directory)
    (p/'Arduino.h').write_text(r'''
#pragma once
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <string>
#include <cstdarg>
#define OUTPUT 1
#define HIGH 1
inline uint32_t clockMs=1000;
inline void pinMode(int,int){} inline void digitalWrite(int,int){}
inline void delay(int n){clockMs+=n;}
inline uint32_t millis(){return clockMs;}
inline uint32_t micros(){static uint32_t n=0;return n+=100;}
struct SerialType {
 std::string out;int baud=115200;
 void begin(int b){baud=b;}void flush(){}void updateBaudRate(int b){baud=b;}
 int available(){return 0;}char read(){return 0;}
 void println(const char*s){out+=s;out+='\n';}
 void write(const uint8_t*p,int n){out.append((const char*)p,n);}
 void printf(const char*f,...){char b[2048];va_list a;va_start(a,f);vsnprintf(b,sizeof(b),f,a);va_end(a);out+=b;}
};inline SerialType Serial;
''')
    (p/'SPI.h').write_text('#pragma once\nstruct SPIType {void begin(int,int,int,int){}};inline SPIType SPI;\n')
    (p/'Adafruit_PN532.h').write_text(r'''
#pragma once
#include "Arduino.h"
#include "SPI.h"
inline uint8_t replyLength=204;
struct Adafruit_PN532 {
 uint32_t tapWriteUs=0,tapAckUs=0,tapWaitUs=0,tapReadUs=0;void setBulkRead(bool){}
 Adafruit_PN532(int,SPIType*){}bool begin(){return true;}
 uint32_t getFirmwareVersion(){return 0x32010600;}bool SAMConfig(){return true;}
 bool setPassiveActivationRetries(int){return true;}bool inListPassiveTarget(){return true;}
 void setReadyPollInterval(uint8_t){}
 bool inDataExchange(uint8_t*,uint8_t,uint8_t*r,uint8_t*l){*l=replyLength;for(int i=0;i<*l;i++)r[i]=i;return true;}
};
''')
    (p/'test.cpp').write_text('#include '+json.dumps(str(root/'src/main.cpp'))+r'''
#include <iostream>
#include <cassert>
void run(const char *s){char b[160];strcpy(b,s);command(b);}
int main(){
 ready=true;selectedTarget=true;
 run("TUNE 1 1");run("TUNE 2 0");run("BAUD 3 460800");
 assert(serialBaud==460800 && Serial.baud==460800);
 run("BAUD 4 230400");run("BAUD 5 115200 extra");
 for(int n=1;n<=204;n++){replyLength=n;run("APDUB 6 8033000000");}
 replyLength=204;run("APDU 7 8033000000");
 clockMs+=3001;loop();assert(serialBaud==115200 && Serial.baud==115200);
 run("TUNE 8 10");assert(pollMs==10);
 initStage="firmware_read_failed";chipVersion=0xffffffff;run("STATUS 999999999");
 run("SPI 10 1");assert(bulkRead);run("SPI 11 2");run("SPI 12 0");assert(!bulkRead);
 std::cout<<Serial.out;
}
''')
    binary=p/'test'
    subprocess.run(['g++','-std=c++17','-fsanitize=address,undefined','-I',str(p),'-DTAP_CS=27','-DTAP_SCK=18','-DTAP_MISO=19','-DTAP_MOSI=23',str(p/'test.cpp'),'-o',str(binary)],check=True)
    result=subprocess.run([str(binary)],check=True,capture_output=True,text=True,env={**os.environ,'ASAN_OPTIONS':'detect_leaks=0'})
    rows=[json.loads(line) for line in result.stdout.splitlines()]
    assert rows[0]['poll_ms']==1 and rows[1]['code']=='bad_profile'
    assert rows[2]['serial_baud']==460800
    assert rows[3]['code']==rows[4]['code']=='bad_baud'
    for length,row in enumerate(rows[5:209],1):
        assert base64.b64decode(row['response_b64'],validate=True)==bytes(range(length))
        assert row['exchange_us']==100 and row['poll_ms']==1
    assert bytes.fromhex(rows[209]['response'])==bytes(range(204))
    assert rows[210]['poll_ms']==10
    assert rows[211]['transport']==2 and rows[211]['serial_baud']==115200
    assert rows[212]['bulk_read'] is True and rows[213]['code']=='bad_spi_mode' and rows[214]['bulk_read'] is False
    assert rows[211]['chip_version']==0xffffffff and rows[211]['stage']=='firmware_read_failed'
    print('Firmware command-handler checks passed: all 204 Base64 lengths, legacy hex, baud handshake, idle fallback, invalid profiles; ASan/UBSan clean (leak checker disabled). Hardware mocked.')
