import struct
with open('docs/eawpats/trumpet.pat', 'rb') as f:
    f.seek(198)
    samples = f.read(1)[0]
    f.seek(239)
    for i in range(samples):
        hdr = f.read(96)
        lengthInBytes = struct.unpack('<I', hdr[8:12])[0]
        modes = hdr[55]
        print(f"Sample {i}: len={lengthInBytes}, mode={modes}")
        f.seek(lengthInBytes, 1)
