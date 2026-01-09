import struct
import sys
with open('docs/eawpats/synstr1.pat', 'rb') as f:
    hdr = f.read(239)
    insts = hdr[82]
    print(f"Instruments: {insts}")
    for i in range(insts):
        # Read Instrument Header
        inst_hdr = f.read(6) # Actually, the instrument header starts with 2 bytes ID, 16 bytes name, 4 bytes size, 1 byte layers... wait!
        # The instrument header doesn't exist in all format versions!
        # GF1PATCH110 vs GF1PATCH100?
        # Let's check the size
