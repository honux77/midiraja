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
    lpf_cutoff = chromosone['lpf_cutoff']
    dither_amp = chromosone['dither_amp']
    pm_overdrive = chromosone['pm_overdrive']

    sampleRate = 44100
    oversample = 32
    oversampledRate = sampleRate * oversample
    pwmCarrierStep = (22050.0 / sampleRate) * 2.0
    note_freq = 261.63 # C4
    fmRatio = 1.0
    fmIndex = 1.1

    phase = [0.0] * voices
    modPhase = [0.0] * voices
    sigmaDeltaError = 0.0
    lpfState = 0.0
    lpfState2 = 0.0
    pwmCarrierPhase = -1.0

    frames = 8192 # Increased frames for better low-frequency resolution
    buffer = np.zeros(frames)
    
    for i in range(frames):
        sumPwm = 0.0
        time = i / sampleRate
        decay = max(0.0, 1.0 - (time / 0.5))
        
        for o in range(oversample):
            analogMix = 0.0
            
            for v in range(voices):
                out = 0.0
                if synth_mode == 'pm':
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
            analogMix /= voices
            
            if mux_mode == 'dsd':
                dither1 = np.random.random() * 2.0 - 1.0
                dither2 = np.random.random() * 2.0 - 1.0
                tpdfDither = (dither1 + dither2) * dither_amp
                sigmaDeltaError += (analogMix + tpdfDither)
                outBit = 1.0 if sigmaDeltaError > 0.0 else -1.0
                sigmaDeltaError -= outBit
                sumPwm += outBit

        rawPwm = sumPwm / oversample
        cleanSignal = rawPwm # Bypass DC blocker for DSD
        
        lpfState += lpf_cutoff * (cleanSignal - lpfState)
        lpfState2 += lpf_cutoff * (lpfState - lpfState2)
        buffer[i] = max(-1.0, min(1.0, lpfState2))

    return buffer

def fitness_function(buffer):
    frames = len(buffer)
    fft_result = np.fft.rfft(buffer)
    freqs = np.fft.rfftfreq(frames, 1.0/44100.0)
    mags = np.abs(fft_result)
    
    # Maximize Fundamental (The Music)
    fund_mask = (freqs >= 200) & (freqs <= 800)
    fund_energy = np.sum(mags[fund_mask])
    
    # Penalize High-Frequency Aliasing/Quantization Noise (The Squeak/Hiss)
    noise_mask = freqs > 8000.0
    noise_energy = np.sum(mags[noise_mask])
    
    score = fund_energy - (noise_energy * 8.0)
    return score

def create_random_chromosome():
    return {
        'lpf_cutoff': random.uniform(0.01, 0.40), # Expand search space
        'dither_amp': random.uniform(0.0, 0.30),
        'pm_overdrive': random.uniform(0.0, 5.0)
    }

def mutate(chrom):
    new_chrom = chrom.copy()
    # 50% chance of mutation per gene, much wider variance
    if random.random() < 0.5: 
        # 10% chance of absolute catastrophe (completely new random value to break local minima)
        if random.random() < 0.1: new_chrom['lpf_cutoff'] = random.uniform(0.01, 0.40)
        else: new_chrom['lpf_cutoff'] = max(0.01, min(0.99, new_chrom['lpf_cutoff'] + random.uniform(-0.15, 0.15)))
        
    if random.random() < 0.5: 
        if random.random() < 0.1: new_chrom['dither_amp'] = random.uniform(0.0, 0.30)
        else: new_chrom['dither_amp'] = max(0.0, min(0.5, new_chrom['dither_amp'] + random.uniform(-0.05, 0.05)))
        
    if random.random() < 0.5: 
        if random.random() < 0.1: new_chrom['pm_overdrive'] = random.uniform(0.0, 5.0)
        else: new_chrom['pm_overdrive'] = max(0.0, min(10.0, new_chrom['pm_overdrive'] + random.uniform(-1.5, 1.5)))
    return new_chrom

# Run the aggressive GA
np.random.seed(42)
random.seed(42)

print("Starting AGGRESSIVE Genetic Optimization [Synth: PM, Mux: DSD, Voices: 4]")
POPULATION_SIZE = 40
GENERATIONS = 15

population = [create_random_chromosome() for _ in range(POPULATION_SIZE)]

for gen in range(GENERATIONS):
    scores = []
    for chrom in population:
        buffer = simulate_dsp(chrom, 'pm', 'dsd', 4)
        score = fitness_function(buffer)
        scores.append((score, chrom))
    
    scores.sort(key=lambda x: x[0], reverse=True)
    best_score = scores[0][0]
    
    print(f"Gen {gen+1:02d} | Best Score: {best_score:8.1f} | LPF: {scores[0][1]['lpf_cutoff']:5.3f} | Dither: {scores[0][1]['dither_amp']:5.3f} | OD: {scores[0][1]['pm_overdrive']:5.3f}")
    
    # Elitism: Keep top 2 unconditionally
    next_gen = [scores[0][1], scores[1][1]]
    
    # Roulette Wheel / Tournament Selection for the rest
    # Bias selection towards higher scores, but allow weaker ones to breed to maintain diversity
    selection_pool = [x[1] for x in scores[:POPULATION_SIZE//2]] # Top 50% breed
    
    while len(next_gen) < POPULATION_SIZE:
        parent1 = random.choice(selection_pool)
        parent2 = random.choice(selection_pool)
        
        # Crossover (Mix genes from two parents)
        child = {
            'lpf_cutoff': parent1['lpf_cutoff'] if random.random() < 0.5 else parent2['lpf_cutoff'],
            'dither_amp': parent1['dither_amp'] if random.random() < 0.5 else parent2['dither_amp'],
            'pm_overdrive': parent1['pm_overdrive'] if random.random() < 0.5 else parent2['pm_overdrive']
        }
        
        # Mutate the child
        child = mutate(child)
        next_gen.append(child)
        
    population = next_gen

print("\nNEW AGGRESSIVE GOD PARAMETERS FOUND:")
for k, v in scores[0][1].items():
    if isinstance(v, bool): print(f"{k}: {v}")
    else: print(f"{k}: {v:.4f}")

