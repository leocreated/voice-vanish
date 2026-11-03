from pathlib import Path
import struct
import sys

def varint(data, pos):
    value = shift = 0
    while True:
        b = data[pos]; pos += 1
        value |= (b & 127) << shift
        if b < 128: return value, pos
        shift += 7

def fields(data):
    pos = 0
    while pos < len(data):
        key, pos = varint(data, pos)
        wire = key & 7
        if wire == 2:
            size, pos = varint(data, pos)
            value = data[pos:pos+size]; pos += size
        elif wire == 5:
            value = data[pos:pos+4]; pos += 4
        elif wire == 0:
            value, pos = varint(data, pos)
        elif wire == 1:
            value = data[pos:pos+8]; pos += 8
        else: raise ValueError(wire)
        yield key >> 3, value

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / 'sherpa-onnx-streaming-zipformer-en-2023-06-26'
pieces = []
for key, value in fields((root / 'bpe.model').read_bytes()):
    if key == 1:
        piece = dict(fields(value))
        pieces.append(piece[1].decode() + '\t' + str(struct.unpack('<f', piece[2])[0]))
(root / 'bpe.vocab').write_text('\n'.join(pieces) + '\n', encoding='utf-8')
print('Exported', len(pieces), 'SentencePiece vocabulary entries')
