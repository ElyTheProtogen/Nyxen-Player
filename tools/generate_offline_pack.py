#!/usr/bin/env python3
"""Generate Nyxen's original offline wallpapers, album covers, and sample audio."""
import math, os, random, struct, zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
DRAWABLE = os.path.join(ROOT, "drawable-nodpi")
RAW = os.path.join(ROOT, "raw")
os.makedirs(DRAWABLE, exist_ok=True)
os.makedirs(RAW, exist_ok=True)

def png(path, width, height, palette, seed, cover=False):
    rng = random.Random(seed)
    rows = []
    c0, c1, c2 = palette
    for y in range(height):
        fy = y / max(1, height - 1)
        row = bytearray([0])
        for x in range(width):
            fx = x / max(1, width - 1)
            glow = max(0.0, 1.0 - math.sqrt(((fx - (0.32 if seed % 2 else .72)) * 1.15) ** 2 + ((fy - .38) * .72) ** 2))
            wave = .5 + .5 * math.sin(fx * 9.0 + fy * 4.5 + seed)
            mix = max(0.0, min(1.0, fy * .72 + glow * .22 + wave * .06))
            for k in range(3):
                base = c0[k] * (1-mix) + c2[k] * mix
                base = base * (1-glow*.32) + c1[k] * glow*.32
                # restrained film grain makes the artwork feel tactile without huge files
                grain = rng.randrange(-3, 4)
                row.append(max(0, min(255, int(base + grain))))
        rows.append(bytes(row))
    raw = b"".join(rows)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">2I5B", width, height, 8, 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 8))
    data += chunk(b"IEND", b"")
    with open(path, "wb") as f: f.write(data)

wallpapers = [
    ((5,6,11),(47,39,70),(7,9,17)),
    ((9,4,20),(111,42,133),(12,7,25)),
    ((2,12,23),(10,105,119),(4,9,21)),
    ((5,25,19),(46,99,100),(17,9,34)),
    ((40,12,7),(205,83,28),(28,7,14)),
    ((4,19,38),(20,115,173),(2,7,22)),
    ((22,8,42),(93,53,164),(8,10,29)),
    ((13,25,45),(35,152,169),(57,27,89)),
]
for i, p in enumerate(wallpapers, 1):
    png(os.path.join(DRAWABLE, f"nyxen_wallpaper_{i:02d}.png"), 720, 1600, p, i*17)
covers = [
    ((7,8,16),(95,53,124),(12,10,25)),
    ((3,13,22),(0,151,172),(5,12,25)),
    ((8,24,21),(70,142,132),(13,10,30)),
]
for i, p in enumerate(covers, 1):
    png(os.path.join(DRAWABLE, f"nyxen_cover_{i:02d}.png"), 900, 900, p, i*41, True)

# 44.1 kHz, stereo, 16-bit, 48-second original instrumental soundscapes.
rate, seconds = 44100, 48
tracks = [
    ("nyxen_sample_01.wav", (55.0, 82.41, 110.0, 164.81), 0),
    ("nyxen_sample_02.wav", (65.41, 98.0, 130.81, 261.63), 1),
    ("nyxen_sample_03.wav", (73.42, 110.0, 146.83, 220.0), 2),
]
for filename, notes, style in tracks:
    path = os.path.join(RAW, filename)
    frames = rate * seconds
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 36 + frames*4) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 2, rate, rate*4, 4, 16) + b"data" + struct.pack("<I", frames*4))
        for i in range(frames):
            t = i / rate
            fade = min(1.0, t/2.0, (seconds-t)/2.0)
            slow = .5 + .5*math.sin(2*math.pi*t/(7.0 if style == 1 else 13.0))
            weights = (.20,.12,.08,.05) if style == 0 else ((.13,.10,.07,.025+slow*.06) if style == 1 else (.12,.08,.06,.02+slow*.05))
            carrier = sum(math.sin(2*math.pi*n*t)*w for n,w in zip(notes,weights))
            shimmer = math.sin(2*math.pi*(523.25 if style == 1 else 440.0)*t + math.sin(2*math.pi*t/9.0))*(.025+.025*slow)
            pulse = math.sin(2*math.pi*2*t)*.025 if style == 1 else 0.0
            noise = (math.sin(i*12.9898 + style*78.233)*43758.5453 % 1.0 - .5)*.012
            base = (carrier + shimmer + pulse + noise)*fade
            pan = math.sin(2*math.pi*t/17.0)*.12
            left = int(max(-.95,min(.95,base*(1-pan)))*32767)
            right = int(max(-.95,min(.95,base*(1+pan)))*32767)
            f.write(struct.pack("<hh", left, right))
print("Generated 8 wallpapers, 3 album covers, and 3 original offline tracks.")
