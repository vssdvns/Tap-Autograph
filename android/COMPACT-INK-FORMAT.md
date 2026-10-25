# TAG1 compact handwriting format — prototype v1

TAG1 stores vector strokes and relative playback timing, not a bitmap or payment credential. File extension: `.tai`. It is unencrypted. CRC32 detects accidental corruption, not malicious changes. This format is not yet an NFC service, a payment confirmation, or a standardized NFC record.

## Envelope (13 bytes)

| Offset | Field | Encoding |
| --- | --- | --- |
| 0 | Magic/version | ASCII TAG1 |
| 4 | Flags | 0 = raw, 1 = zlib (RFC 1950) |
| 5 | Decoded body size | unsigned 32-bit big endian |
| 9 | CRC32 of decoded body | unsigned 32-bit big endian |
| 13 | Body | raw or zlib-compressed |

Encoder uses zlib only when it reduces size. Max packet 65,536 bytes; max decoded body 1,048,576 bytes. Reject unknown flags, invalid lengths, checksum mismatch, malformed/trailing data and decompression beyond the declared bound.

## Body

Fixed prefix: aspect ratio unsigned16 / 1024; normalized line width unsigned16 / 65535; ARGB unsigned32, all big endian. Then unsigned LEB128 stroke count. For each stroke: unsigned LEB128 point count followed by points.

Each point contains zigzag unsigned LEB128 x delta, zigzag unsigned LEB128 y delta, unsigned LEB128 timestamp delta in milliseconds, one pressure byte divided by 127.5, and one Android tool-type byte (0–4). Coordinates are quantized to integers 0–4095. Previous x/y reset to zero per stroke. Previous timestamp continues across strokes; the first source timestamp is subtracted. Source timestamps must be nondecreasing. Maximum total duration is 24 hours.

Counts: 1–1,000 nonempty strokes and at most 30,000 total points. All samples are retained. Coordinate quantization error is at most 0.5/4095 plus float rounding (tested tolerance adds 1e-7); pressure error is at most 0.5/127.5 plus float rounding. Aspect is clamped to representable valid values near the open upper boundary 20. Relative integer millisecond timing and tool type are exact. Original UUID is omitted; decoder derives an ID from the first 12 bytes of SHA256(packet). This ID is not proof of origin. Varints must be canonical, no more than five bytes, with field-specific bounds.

`tools/decode-compact.cjs` is an independent Node reference decoder: `node tools/decode-compact.cjs sample.tai`. Android unit tests generate synthetic interoperability fixtures under `app/build/codec-fixtures`.

A future transport needs its own session binding, consent, chunking, retries and authenticated encryption. The UI's 192-byte block count is a planning estimate only. Actual NFC timing and compatibility require hardware measurements.

## TAG3 lossless column layout (v0.15)

Uses the same 13-byte envelope as TAG1: ASCII TAG3, flag 0 (raw) or 1 (zlib), big-endian
u32 body length, big-endian CRC32 of the uncompressed body. Packet limit 65536 bytes,
body limit 1048576. Inflation is bounded; trailing compressed/raw data is rejected.

Body: the same 8 property bytes as TAG1 (aspect u16/1024, width u16/65535, ARGB u32),
canonical varint stroke count, all stroke point counts as canonical varints, followed by
five columns across all points in stroke order: zigzag x deltas (varint <=8190), zigzag
y deltas (<=8190), time deltas (<=86400000), pressure bytes, tool bytes. Coordinate
accumulators restart per stroke; time accumulates globally. At most 1000 nonempty strokes,
30000 points, x/y within 0..4095, total time <=86400000, tools 0..4. Pressure is byte/127.5.

Columns preserve every TAG1 quantized value. The NFC encoder tries TAG1, eligible TAG2,
and TAG3 and chooses the smallest; saved artwork is not changed. Merchant update required.
