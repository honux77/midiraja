import numpy as np

data = np.fromfile("verify_pwm.raw", dtype=np.int16)
left = data[0::2] / 32767.0

# Print info about the transition from sound to silence
# Sound ends at sample 22050. Silence logic kicks in after 100ms (4410 samples) = 26460.

print("--- Audio Sample Dump ---")
print("During sine wave (Sample 10000):", left[10000:10005])
print("Just after sine ends (Sample 22050):", left[22050:22055])
print("During the 100ms IIR decay window (Sample 25000):", left[25000:25005])
print("Right when Silence Gate triggers (Sample 26458-26465):")
print(left[26458:26466])

print("\n--- Silence check ---")
last_second = left[-44100:]
max_val = np.max(np.abs(last_second))
print(f"Max absolute value in the last 1 second: {max_val}")

