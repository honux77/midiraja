import struct
with open('docs/eawpats/acpiano.pat', 'rb') as f:
    f.seek(239) # skip global header
    f.seek(63, 1) # skip inst header
    sh = f.read(96)
    modes = sh[49]
    print(f"Modes: {modes} (16bit={modes & 1}, unsigned={modes & 2})")
