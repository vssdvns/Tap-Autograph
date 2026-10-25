#include <Arduino.h>
#include <SPI.h>
#include <Adafruit_PN532.h>

// Provisional classic ESP32 SPI profile. Verify module power/mode before wiring.
Adafruit_PN532 nfc(TAP_CS, &SPI);
bool ready = false;
bool selectedTarget = false;
uint32_t chipVersion = 0;
uint8_t initAttempts = 0;
const char *initStage = "not_started";

// Preserve the TAP1 serial protocol; extra status fields are diagnostic only.
void initializeReader() {
  ready = false;
  selectedTarget = false;
  chipVersion = 0;
  for (initAttempts = 1; initAttempts <= 3; ++initAttempts) {
    initStage = "spi_begin_failed";
    if (nfc.begin()) {
      initStage = "firmware_read_failed";
      chipVersion = nfc.getFirmwareVersion();
      if (chipVersion && ((chipVersion >> 24) & 0xff) == 0x32) {
        initStage = "sam_config_failed";
        if (nfc.SAMConfig()) {
          initStage = "retry_config_failed";
          if (nfc.setPassiveActivationRetries(0x01)) {
            ready = true;
            initStage = "ready";
            break;
          }
        }
      }
    }
    // Retry transient startup failures, never change wiring or interface mode.
    if (initAttempts < 3) delay(250);
  }
  if (initAttempts > 3) initAttempts = 3;
}

char line[160];
size_t used = 0;
bool overflow = false;
uint8_t pollMs = 10;
bool bulkRead = false;
uint32_t serialBaud = 115200;
uint32_t lastCommandMs = 0;

void reply(uint32_t id, const char *event, const char *fields) {
  Serial.printf("{\"id\":%lu,\"event\":\"%s\",%s}\n", (unsigned long)id, event, fields);
}
int nibble(char c) { if(c>='0'&&c<='9')return c-'0';if(c>='A'&&c<='F')return c-'A'+10;if(c>='a'&&c<='f')return c-'a'+10;return -1; }
void command(char *text) {
  char *save = nullptr;char *op=strtok_r(text," ",&save);char *idText=strtok_r(nullptr," ",&save);
  if(!op || !idText || strlen(idText)>9)return;
  for(char *p=idText;*p;p++)if(*p<'0'||*p>'9')return;
  uint32_t id=strtoul(idText,nullptr,10);
  lastCommandMs = millis();
  if(strcmp(op,"STATUS")==0){
    char fields[512];snprintf(fields,sizeof(fields),"\"firmware\":\"tap-reader-0.5\",\"pn532\":%s,\"mode\":\"SPI\",\"sck\":%d,\"miso\":%d,\"mosi\":%d,\"cs\":%d,\"spi_bulk\":1,\"spi_timing\":1,\"transport\":2,\"serial_baud\":%lu,\"performance\":1,\"diagnostics\":1,\"stage\":\"%s\",\"attempts\":%u,\"chip_version\":%lu",ready?"true":"false",TAP_SCK,TAP_MISO,TAP_MOSI,TAP_CS,(unsigned long)serialBaud,initStage,initAttempts,(unsigned long)chipVersion);reply(id,"status",fields);return;
  }
  if(strcmp(op,"SPI")==0){
    char *mode=strtok_r(nullptr," ",&save);
    if(!mode || (strcmp(mode,"0") && strcmp(mode,"1")) || strtok_r(nullptr," ",&save)){reply(id,"error","\"code\":\"bad_spi_mode\"");return;}
    bulkRead=strcmp(mode,"1")==0;nfc.setBulkRead(bulkRead);reply(id,"spi",bulkRead?"\"bulk_read\":true":"\"bulk_read\":false");return;
  }
  if(strcmp(op,"BAUD")==0){
    char *rate=strtok_r(nullptr," ",&save);
    if(!rate || (strcmp(rate,"115200") && strcmp(rate,"460800")) || strtok_r(nullptr," ",&save)){reply(id,"error","\"code\":\"bad_baud\"");return;}
    const uint32_t nextBaud=strtoul(rate,nullptr,10);
    char fields[64];snprintf(fields,sizeof(fields),"\"serial_baud\":%lu",(unsigned long)nextBaud);
    reply(id,"baud",fields);Serial.flush();delay(25);Serial.updateBaudRate(nextBaud);serialBaud=nextBaud;used=0;
    lastCommandMs=millis();return;
  }
  if(strcmp(op,"TUNE")==0){
    char *profile=strtok_r(nullptr," ",&save);
    if(!profile || (strcmp(profile,"1") && strcmp(profile,"10")) || strtok_r(nullptr," ",&save)){reply(id,"error","\"code\":\"bad_profile\"");return;}
    pollMs=atoi(profile);nfc.setReadyPollInterval(pollMs);
    char fields[48];snprintf(fields,sizeof(fields),"\"poll_ms\":%u",pollMs);reply(id,"tuned",fields);return;
  }
  if(!ready){reply(id,"error","\"code\":\"pn532_missing\"");return;}
  if(strcmp(op,"POLL")==0){selectedTarget=nfc.inListPassiveTarget();reply(id,"target",selectedTarget?"\"found\":true":"\"found\":false");return;}
  const bool base64Reply=strcmp(op,"APDUB")==0;
  if(strcmp(op,"APDU")!=0 && !base64Reply){reply(id,"error","\"code\":\"unknown_command\"");return;}
  char *hex=strtok_r(nullptr," ",&save);
  if(!selectedTarget){reply(id,"error","\"code\":\"no_target\"");return;}
  if(!hex || strlen(hex)<10 || strlen(hex)>90 || strlen(hex)%2 || strtok_r(nullptr," ",&save)){reply(id,"error","\"code\":\"bad_command\"");return;}
  uint8_t bytes[45];uint8_t count=strlen(hex)/2;
  for(uint8_t i=0;i<count;i++){int a=nibble(hex[2*i]),b=nibble(hex[2*i+1]);if(a<0||b<0){reply(id,"error","\"code\":\"bad_hex\"");return;}bytes[i]=(a<<4)|b;}
  uint8_t response[204];uint8_t length=sizeof(response);
  const uint32_t exchangeStarted = micros();
  if(!nfc.inDataExchange(bytes,count,response,&length)){selectedTarget=false;reply(id,"error","\"code\":\"exchange_failed\"");return;}
  const uint32_t exchangeUs = uint32_t(micros() - exchangeStarted);
  // One bounded write avoids hundreds of printf calls / tiny UART submissions.
  char output[768];
  int offset=snprintf(output,sizeof(output),"{\"id\":%lu,\"event\":\"apdu\",\"%s\":\"",(unsigned long)id,base64Reply?"response_b64":"response");
  if(base64Reply){
    const char *digits="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    for(uint16_t i=0;i<length;i+=3){
      const uint32_t word=(uint32_t(response[i])<<16)|(i+1<length?uint32_t(response[i+1])<<8:0)|(i+2<length?response[i+2]:0);
      output[offset++]=digits[(word>>18)&63];output[offset++]=digits[(word>>12)&63];
      output[offset++]=i+1<length?digits[(word>>6)&63]:'=';output[offset++]=i+2<length?digits[word&63]:'=';
    }
  }else{
    const char *digits="0123456789ABCDEF";
    for(uint16_t i=0;i<length;i++){output[offset++]=digits[response[i]>>4];output[offset++]=digits[response[i]&15];}
  }
  offset+=snprintf(output+offset,sizeof(output)-offset,"\",\"exchange_us\":%lu,\"poll_ms\":%u,\"write_us\":%lu,\"ack_us\":%lu,\"wait_us\":%lu,\"read_us\":%lu,\"bulk_read\":%s}\n",(unsigned long)exchangeUs,pollMs,(unsigned long)nfc.tapWriteUs,(unsigned long)nfc.tapAckUs,(unsigned long)nfc.tapWaitUs,(unsigned long)nfc.tapReadUs,bulkRead?"true":"false");
  Serial.write((const uint8_t *)output,offset);
}
void setup() {
  Serial.begin(115200);delay(400);
  SPI.begin(TAP_SCK,TAP_MISO,TAP_MOSI,TAP_CS);
  pinMode(TAP_CS, OUTPUT);
  digitalWrite(TAP_CS, HIGH);
  delay(250);
  initializeReader();
  Serial.printf("{\"event\":\"boot\",\"firmware\":\"tap-reader-0.5\",\"pn532\":%s}\n",ready?"true":"false");
}
void loop() {
  while(Serial.available()){
    char c=Serial.read();if(c=='\r')continue;
    if(c=='\n'){if(!overflow){line[used]=0;command(line);}else Serial.println("{\"event\":\"error\",\"code\":\"line_too_long\"}");used=0;overflow=false;}
    else if(!overflow){if(used<sizeof(line)-1)line[used++]=c;else overflow=true;}
  }
  if(serialBaud!=115200 && uint32_t(millis()-lastCommandMs)>3000){
    Serial.flush();Serial.updateBaudRate(115200);serialBaud=115200;used=0;overflow=false;
  }
  delay(1);
}
