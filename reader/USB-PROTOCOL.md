# USB serial contract — tap-reader-0.5

115200 baud,8N1,newline terminated ASCII. One request outstanding at a time. Host commands:

- `STATUS <id>` → JSON `{id,event:"status",firmware:"tap-reader-0.5",pn532:BOOL,mode:"SPI",sck:18,miso:19,mosi:23,cs:27}`
- `POLL <id>` → JSON `{id,event:"target",found:BOOL}`; PN532 initiates one-target ISO14443A selection.
- `APDU <id> <HEX>` → JSON `{id,event:"apdu",response:HEX}`; phone response including its two-byte status. Commands5–45 bytes, responses≤204 bytes.
- Failures → JSON `{id,event:"error",code:STRING}`. Boot notifications need not have id. The bridge ignores bounded non-JSON boot text/wrong IDs, caps each reply line at2048 bytes and times out after4s. Firmware caps command lines at159 chars and discards an overlong line until newline.

Host retries reads at most twice (three attempts total), re-polls and re-selects the exact same metadata before retrying. Session changes abort the transfer. SHA256+length are checked on the host, then the local Node server must accept/decode TAG1 before Finish is sent. Lost Finish acknowledgement is reported separately and is not represented as phone completion. Retries after disconnect can retain a snapshot only within the phone's existing60s grant.

The server deduplicates session+hash for2min. After success the bridge waits for the phone to leave before another read. APDU transport is plaintext; unkeyed hashes/checksums do not authenticate it. No serial packets are persisted by the bridge. Do not record a Serial Monitor transcript of sensitive handwriting.
