#include "../lib/Adafruit_PN532/TapSpiRead.h"
#include <cassert>
#include <vector>
struct Device {
 std::vector<uint8_t> stream;size_t position=0;bool selected=false;int begins=0,ends=0,reads=0;
 void beginTransactionWithAssertingCS(){assert(!selected);selected=true;begins++;}
 void endTransactionWithDeassertingCS(){assert(selected);selected=false;ends++;}
 uint8_t transfer(uint8_t command){assert(selected&&command==3);return 0;}
 void transfer(uint8_t *buffer,size_t n){assert(selected);reads++;for(size_t i=0;i<n;i++){assert(buffer[i]==0xFF);buffer[i]=stream.at(position++);}}
 void write_then_read(const uint8_t*c,size_t count,uint8_t*b,size_t n){assert(count==1);beginTransactionWithAssertingCS();transfer(*c);for(size_t i=0;i<n;i++)b[i]=stream.at(position++);endTransactionWithDeassertingCS();}
};
int main(){
 for(size_t n=1;n<=255;n++){
  Device legacy,bulk;for(size_t i=0;i<n;i++){legacy.stream.push_back((i*73+n)&255);bulk.stream=legacy.stream;}
  std::vector<uint8_t>a(n+2,0xA5),b(n+2,0xA5);
  tapSpiRead(&legacy,3,a.data()+1,n,false);tapSpiRead(&bulk,3,b.data()+1,n,true);
  assert(a==b&&a.front()==0xA5&&a.back()==0xA5);assert(bulk.position==n&&legacy.position==n);
  assert(bulk.begins==1&&bulk.ends==1&&bulk.reads==1&&!bulk.selected);
 }
}
