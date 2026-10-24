#include <Arduino.h>
#include <SPI.h>
#include <Adafruit_PN532.h>

// Provisional classic ESP32 SPI profile. Verify module power/mode before wiring.
Adafruit_PN532 nfc(TAP_CS, &SPI);
bool ready = false;
bool selectedTarget = false;
char line[160];
size_t used = 0;
bool overflow = false;

void reply(uint32_t id, const char *event, const char *fields) {
  Serial.printf("{\"id\":%lu,\"event\":\"%s\",%s}\n", (unsigned long)id, event, fields);
}
int nibble(char c) { if(c>='0'&&c<='9')return c-'0';if(c>='A'&&c<='F')return c-'A'+10;if(c>='a'&&c<='f')return c-'a'+10;return -1; }
void command(char *text) {
  char *save = nullptr;char *op=strtok_r(text," ",&save);char *idText=strtok_r(nullptr," ",&save);
  if(!op || !idText || strlen(idText)>9)return;
  for(char *p=idText;*p;p++)if(*p<'0'||*p>'9')return;
  uint32_t id=strtoul(idText,nullptr,10);
  if(strcmp(op,"STATUS")==0){
    char fields[180];snprintf(fields,sizeof(fields),"\"firmware\":\"tap-reader-0.5\",\"pn532\":%s,\"mode\":\"SPI\",\"sck\":%d,\"miso\":%d,\"mosi\":%d,\"cs\":%d",ready?"true":"false",TAP_SCK,TAP_MISO,TAP_MOSI,TAP_CS);reply(id,"status",fields);return;
  }
  if(!ready){reply(id,"error","\"code\":\"pn532_missing\"");return;}
  if(strcmp(op,"POLL")==0){selectedTarget=nfc.inListPassiveTarget();reply(id,"target",selectedTarget?"\"found\":true":"\"found\":false");return;}
  if(strcmp(op,"APDU")!=0){reply(id,"error","\"code\":\"unknown_command\"");return;}
  char *hex=strtok_r(nullptr," ",&save);
  if(!selectedTarget){reply(id,"error","\"code\":\"no_target\"");return;}
  if(!hex || strlen(hex)<10 || strlen(hex)>90 || strlen(hex)%2 || strtok_r(nullptr," ",&save)){reply(id,"error","\"code\":\"bad_command\"");return;}
  uint8_t bytes[45];uint8_t count=strlen(hex)/2;
  for(uint8_t i=0;i<count;i++){int a=nibble(hex[2*i]),b=nibble(hex[2*i+1]);if(a<0||b<0){reply(id,"error","\"code\":\"bad_hex\"");return;}bytes[i]=(a<<4)|b;}
  uint8_t response[204];uint8_t length=sizeof(response);
  if(!nfc.inDataExchange(bytes,count,response,&length)){selectedTarget=false;reply(id,"error","\"code\":\"exchange_failed\"");return;}
  // Only framed replies; never log raw handwriting separately.
  Serial.printf("{\"id\":%lu,\"event\":\"apdu\",\"response\":\"",(unsigned long)id);
  for(uint8_t i=0;i<length;i++)Serial.printf("%02X",response[i]);
  Serial.println("\"}");
}
void setup() {
  Serial.begin(115200);delay(400);
  SPI.begin(TAP_SCK,TAP_MISO,TAP_MOSI,TAP_CS);
  nfc.begin();uint32_t version=nfc.getFirmwareVersion();
  if(version){ready=nfc.SAMConfig() && nfc.setPassiveActivationRetries(0x01);}
  Serial.printf("{\"event\":\"boot\",\"firmware\":\"tap-reader-0.5\",\"pn532\":%s}\n",ready?"true":"false");
}
void loop() {
  while(Serial.available()){
    char c=Serial.read();if(c=='\r')continue;
    if(c=='\n'){if(!overflow){line[used]=0;command(line);}else Serial.println("{\"event\":\"error\",\"code\":\"line_too_long\"}");used=0;overflow=false;}
    else if(!overflow){if(used<sizeof(line)-1)line[used++]=c;else overflow=true;}
  }
  delay(1);
}
