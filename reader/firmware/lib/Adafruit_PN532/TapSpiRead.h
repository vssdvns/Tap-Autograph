#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>

// Same command, dummy bytes, bit order and CS interval as write_then_read.
// Only the number of host SPI calls changes. Other interfaces keep their old path.
template<class Device>
inline void tapSpiRead(Device *device,uint8_t command,uint8_t *buffer,size_t length,bool bulk){
  if(!bulk){device->write_then_read(&command,1,buffer,length);return;}
  device->beginTransactionWithAssertingCS();
  device->transfer(command);memset(buffer,0xFF,length);device->transfer(buffer,length);
  device->endTransactionWithDeassertingCS();
}
