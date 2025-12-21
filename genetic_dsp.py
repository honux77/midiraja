import numpy as np
import math
import random

SINE_LUT_SIZE = 4096
SINE_LUT = np.sin(np.linspace(0, 2 * np.pi, SINE_LUT_SIZE, endpoint=False))

def fastSin(p):
    idx = int(p * SINE_LUT_SIZE)
    if idx < 0: idx = 0
    if idx >= SINE_LUT_SIZE: idx = SINE_LUT_SIZE - 1
    return SINE_LUT[idx]

def simulate_dsp(chromosone, synth_mode, mux_mode, voices):
    use_lpf = chromosone['use_lpf']
    lpf_cutoff = chromosone['lpf_cutoff']
    use_dc_blocker = chromosone['use_dc_blocker']
    dc_r = chromosone['dc_r']
    dither_amp = chromosone['dither_amp']
    pm_overdrive = chromosone['pm_overdrive']

    sampleRate = 44100
    oversample = 32
    oversampledRate = sampleRate * oversample
    pwmCarrierStep = (22050.0 / sampleRate) * 2.0

    note_freq = 261.63 # C4
    fmRatio = 1.0
    actualRatio = 1.005
    fmIndex = 1.1

    phase = [0.0] * voices
    modPhase = [0.0] * voices
    lfoPhase = [0.0] * voices

    sigmaDeltaError = 0.0
    lpfState = 0.0
    lpfState2 = 0.0
    dcBlockerX = 0.0
    dcBlockerY = 0.0
    pwmCarrierPhase = -1.0

    frames = 4096 
    buffer = np.zeros(frames)
    
    for i in range(frames):
        sumPwm = 0.0
        time = i / sampleRate
        decay = max(0.0, 1.0 - (time / 0.5))
        
        for o in range(oversample):
            pwmCarrierPhase += pwmCarrierStep / oversample
            if pwmCarrierPhase > 1.0: pwmCarrierPhase -= 2.0
            
            analogMix = 0.0
            mixedXor = False
            
            for v in range(voices):
                out = 0.0
                if synth_mode == 'xor':
                    phase[v] += note_freq / oversampledRate
                    phase[v] -= math.floor(phase[v])
                    modFreq = note_freq * actualRatio
                    modPhase[v] += modFreq / oversampledRate
                    modPhase[v] -= math.floor(modPhase[v])
                    
                    carrierBit = phase[v] > 0.5
                    modBit = modPhase[v] > 0.5
                    finalBit = carrierBit ^ modBit
                    out = (1.0 if finalBit else -1.0) * decay
                    
                elif synth_mode == 'pm':
                    keyScale = 1.0
                    scaledFmIndex = fmIndex * keyScale
                    envIndex = (scaledFmIndex * 0.1) + (scaledFmIndex * decay)
                    
                    modFreq = note_freq * fmRatio
                    modPhase[v] += modFreq / oversampledRate
                    modPhase[v] -= math.floor(modPhase[v])
                    modulator = fastSin(modPhase[v])
                    
                    phase[v] += note_freq / oversampledRate
                    phase[v] -= math.floor(phase[v])
                    
                    finalPhase = phase[v] + (modulator * (envIndex / (2.0 * math.pi)))
                    finalPhase -= math.floor(finalPhase)
                    
                    rawSine = fastSin(finalPhase)
                    if pm_overdrive > 0.0:
                        out = math.tanh(rawSine * pm_overdrive) * decay
                    else:
                        out = rawSine * decay
                        
                analogMix += out
                
                pwmBit = out > pwmCarrierPhase
                if v == 0: mixedXor = pwmBit
                else: mixedXor ^= pwmBit

            analogMix /= voices
            
            if mux_mode == 'dsd':
                dither1 = np.random.random() * 2.0 - 1.0
                dither2 = np.random.random() * 2.0 - 1.0
                tpdfDither = (dither1 + dither2) * dither_amp
                sigmaDeltaError += (analogMix + tpdfDither)
                outBit = 1.0 if sigmaDeltaError > 0.0 else -1.0
                sigmaDeltaError -= outBit
                sumPwm += outBit
            elif mux_mode == 'pwm':
                pwmBit = analogMix > pwmCarrierPhase
                sumPwm += 1.0 if pwmBit else -1.0
            elif mux_mode == 'xor':
                sumPwm += 1.0 if mixedXor else -1.0

        rawPwm = sumPwm / oversample
        cleanSignal = rawPwm
        
        if use_dc_blocker:
            cleanSignal = rawPwm - dcBlockerX + (dc_r * dcBlockerY)
            dcBlockerX = rawPwm
            dcBlockerY = cleanSignal
            
        if use_lpf:
            lpfState += lpf_cutoff * (cleanSignal - lpfState)
            lpfState2 += lpf_cutoff * (lpfState - lpfState2)
            buffer[i] = max(-1.0, min(1.0, lpfState2))
        else:
            buffer[i] = max(-1.0, min(1.0, cleanSignal))

    return buffer

def fitness_function(buffer):
    frames = len(buffer)
    fft_result = np.fft.rfft(buffer)
    freqs = np.fft.rfftfreq(frames, 1.0/44100.0)
    mags = np.abs(fft_result)
    
    fund_mask = (freqs >= 200) & (freqs <= 800)
    fund_energy = np.sum(mags[fund_mask])
    
    noise_mask = freqs > 5000.0
    noise_energy = np.sum(mags[noise_mask])
    
    dc_mask = freqs < 20.0
    dc_energy = np.sum(mags[dc_mask])
    
    # Strongly penalize high frequency noise and DC offset
    score = fund_energy - (noise_energy * 10.0) - (dc_energy * 2.0)
    return score

def create_random_chromosome():
    return {
        'use_lpf': random.choice([True, False]),
        'lpf_cutoff': random.uniform(0.01, 0.50),
        'use_dc_blocker': random.choice([True, False]),
        'dc_r': random.uniform(0.95, 0.999),
        'dither_amp': random.uniform(0.0, 0.1),
        'pm_overdrive': random.uniform(0.0, 5.0)
    }

def mutate(chrom):
    new_chrom = chrom.copy()
    if random.random() < 0.2: new_chrom['use_lpf'] = not new_chrom['use_lpf']
    if random.random() < 0.4: new_chrom['lpf_cutoff'] = max(0.01, min(0.99, new_chrom['lpf_cutoff'] + random.uniform(-0.1, 0.1)))
    if random.random() < 0.2: new_chrom['use_dc_blocker'] = not new_chrom['use_dc_blocker']
    if random.random() < 0.4: new_chrom['dc_r'] = max(0.90, min(0.999, new_chrom['dc_r'] + random.uniform(-0.01, 0.01)))
    if random.random() < 0.4: new_chrom['dither_amp'] = max(0.0, min(0.2, new_chrom['dither_amp'] + random.uniform(-0.02, 0.02)))
    if random.random() < 0.4: new_chrom['pm_overdrive'] = max(0.0, min(10.0, new_chrom['pm_overdrive'] + random.uniform(-1.0, 1.0)))
    return new_chrom

np.random.seed(1)
random.seed(1)

print("Running Genetic Optimization for [Synth: PM, Mux: DSD, Voices: 4]...")
POPULATION_SIZE = 15
GENERATIONS = 10

population = [create_random_chromosome() for _ in range(POPULATION_SIZE)]

for gen in range(GENERATIONS):
    scores = []
    for chrom in population:
        buffer = simulate_dsp(chrom, 'pm', 'dsd', 4)
        score = fitness_function(buffer)
        scores.append((score, chrom))
    
    scores.sort(key=lambda x: x[0], reverse=True)
    best_score = scores[0][0]
    
    print(f"Gen {gen+1:02d} | Score: {best_score:8.1f} | LPF: {scores[0][1]['lpf_cutoff']:5.3f} | DC Blk: {scores[0][1]['use_dc_blocker']} | Overdrive: {scores[0][1]['pm_overdrive']:5.3f}")
    
    survivors = [x[1] for x in scores[:POPULATION_SIZE//2]]
    next_gen = survivors.copy()
    
    while len(next_gen) < POPULATION_SIZE:
        parent = random.choice(survivors)
        child = mutate(parent)
        next_gen.append(child)
        
    population = next_gen

print("\nULTIMATE GOD PARAMETERS FOUND:")
for k, v in scores[0][1].items():
    if isinstance(v, bool): print(f"{k}: {v}")
    else: print(f"{k}: {v:.4f}")
