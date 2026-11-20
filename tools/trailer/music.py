"""Original trailer music (chiptune, 120 BPM), made from scratch so YouTube's Content ID has nothing to match.

Sections follow the story, in bars of 2 s:
  intro   bars 0-4    morning: pad and a bell arpeggio
  groove  bars 5-21   the Bubies at work: drums, bass, lead
  dusk    bars 22-24  night falls: drums drop out, the pad stays
  rise    bars 25-26  into the cave: a kick that speeds up and a riser
  party   bars 27-30  the DJ: everything, louder
  outro   bars 31-32  logo: one chord rings out

usage: python tools/trailer/music.py   (writes runs/trailer/out/music.wav)
"""
import os

import numpy as np
import soundfile as sf

SR = 44100
BPM = 120
BEAT = 60 / BPM
BAR = BEAT * 4
BARS = 33
LENGTH = BAR * BARS
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'runs', 'trailer', 'out', 'music.wav')

rng = np.random.default_rng(7)
left = np.zeros(int(LENGTH * SR) + SR)
right = np.zeros_like(left)


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def env(n, attack=0.005, release=0.1, sustain=1.0):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((n / SR - t) / max(release, 1e-4), 0, 1)
    return a * r * sustain


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):  # small buffers only
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def add(start, sig, gain=1.0, pan=0.0):
    i = int(start * SR)
    j = min(len(left), i + len(sig))
    sig = sig[:j - i] * gain
    left[i:j] += sig * (1 - max(0, pan))
    right[i:j] += sig * (1 + min(0, pan))


def square(f, n, duty=0.5):
    t = np.arange(n) / SR
    return np.where((t * f) % 1 < duty, 1.0, -1.0)


def saw(f, n):
    t = np.arange(n) / SR
    return 2 * ((t * f) % 1) - 1


def tri(f, n):
    return 2 * np.abs(saw(f, n)) - 1


def kick(t, gain=1.0):
    n = int(0.35 * SR)
    tt = np.arange(n) / SR
    f = 50 + 110 * np.exp(-tt * 30)
    sig = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9)
    add(t, sig, 0.9 * gain)


def snare(t, gain=1.0):
    n = int(0.2 * SR)
    tt = np.arange(n) / SR
    noise = rng.standard_normal(n) * np.exp(-tt * 22)
    tone = np.sin(2 * np.pi * 190 * tt) * np.exp(-tt * 30)
    add(t, noise * 0.35 + tone * 0.4, 0.8 * gain)


def hat(t, gain=1.0, pan=0.25):
    n = int(0.05 * SR)
    noise = rng.standard_normal(n)
    noise = noise - np.concatenate([[0], noise[:-1]])  # crude high-pass
    add(t, noise * np.exp(-np.arange(n) / SR * 80), 0.12 * gain, pan)


def note(t, midi, dur, wave, gain, pan=0.0, attack=0.005, release=0.08, vibrato=0.0):
    n = int(dur * SR)
    if vibrato:
        tt = np.arange(n) / SR
        f = hz(midi) * (1 + vibrato * np.sin(2 * np.pi * 5.5 * tt) * np.clip(tt * 3, 0, 1))
        phase = np.cumsum(f) / SR
        sig = np.where(phase % 1 < 0.5, 1.0, -1.0) if wave == 'square' else np.sin(2 * np.pi * phase)
    elif wave == 'square':
        sig = square(hz(midi), n)
    elif wave == 'pulse':
        sig = square(hz(midi), n, 0.25)
    elif wave == 'tri':
        sig = tri(hz(midi), n)
    elif wave == 'saw':
        sig = saw(hz(midi), n)
    else:
        sig = np.sin(2 * np.pi * hz(midi) * np.arange(n) / SR)
    add(t, sig * env(n, attack, release), gain, pan)


def bell(t, midi, gain=0.12, pan=0.0):
    n = int(1.2 * SR)
    tt = np.arange(n) / SR
    sig = (np.sin(2 * np.pi * hz(midi) * tt) + 0.4 * np.sin(2 * np.pi * hz(midi) * 2.76 * tt) * np.exp(-tt * 6))
    add(t, sig * np.exp(-tt * 3.5), gain, pan)


def pad(t, chord, dur, gain=0.05, cutoff=1800):
    n = int(dur * SR)
    sig = np.zeros(n)
    for m in chord:
        for det in (-0.12, 0.0, 0.12):
            sig += saw(hz(m + det), n)
    sig = lowpass(sig, cutoff) / (len(chord) * 3)
    add(t, sig * env(n, 0.4, 0.6), gain, -0.2)
    add(t, sig * env(n, 0.45, 0.6), gain, 0.2)


# chords (MIDI): C - G - Am - F, and the party turns it around: Am - F - C - G
C, G, Am, F = [60, 64, 67], [55, 59, 62], [57, 60, 64], [53, 57, 60]
WORK = [C, G, Am, F]
PARTY = [Am, F, C, G]
# lead motif per chord, as (beat, semitones from chord root + 12, beats)
MOTIF = [[(0, 12, 1), (1, 16, 0.5), (1.5, 19, 0.5), (2, 16, 1), (3, 14, 1)],
         [(0, 14, 1.5), (1.5, 12, 0.5), (2, 11, 1), (3, 7, 1)],
         [(0, 12, 0.5), (0.5, 16, 0.5), (1, 19, 1), (2, 21, 1), (3, 19, 1)],
         [(0, 17, 1), (1, 16, 1), (2, 14, 1.5), (3.5, 12, 0.5)]]

for bar in range(BARS):
    t0 = bar * BAR
    if bar <= 4:  # intro
        chord = WORK[bar % 4]
        pad(t0, chord, BAR + 0.3, 0.13 + 0.02 * bar)
        for i in range(8):
            bell(t0 + i * BEAT / 2, chord[i % 3] + 12 + (12 if i % 4 == 3 else 0), 0.16 + 0.02 * bar, (-0.4, 0.4)[i % 2])
        if bar == 4:
            for i in range(8):
                snare(t0 + 2 * BEAT + i * BEAT / 4, 0.25 + i * 0.08)  # fill into the groove
    elif bar <= 21:  # groove
        chord = WORK[bar % 4]
        pad(t0, chord, BAR + 0.2, 0.045, 2200)
        for b in range(4):
            kick(t0 + b * BEAT)
            if b in (1, 3):
                snare(t0 + b * BEAT)
            hat(t0 + b * BEAT + BEAT / 2)
            hat(t0 + b * BEAT, 0.6, -0.25)
        root = chord[0] - 24
        for e in range(8):
            note(t0 + e * BEAT / 2, root + (12 if e % 2 else 0), BEAT / 2 * 0.9, 'tri', 0.22)
        for i in range(16):
            note(t0 + i * BEAT / 4, chord[i % 3] + 24, BEAT / 4 * 0.7, 'pulse', 0.025, (-0.5, 0.5)[i % 2])
        if bar >= 9:  # the lead joins after the first round
            for beat, semi, length in MOTIF[bar % 4]:
                note(t0 + beat * BEAT, chord[0] + semi, length * BEAT * 0.92, 'square', 0.05, 0.0, 0.01, 0.08, 0.006)
    elif bar <= 24:  # dusk: the drums leave, a slow pad and a lonely bell
        chord = [Am, F, G][bar - 22]
        pad(t0, chord, BAR + 0.5, 0.07, 900 + 300 * (bar - 22))
        for i in range(4):
            bell(t0 + i * BEAT, chord[(i * 2) % 3] + 12, 0.06, (-0.5, 0.5)[i % 2])
        note(t0, chord[0] - 24, BAR, 'tri', 0.16, attack=0.3, release=0.5)
    elif bar <= 26:  # rise: kicks speed up, a noise sweep over two bars
        first = bar == 25
        n = int(BAR * SR)
        tt = np.arange(n) / SR + (0 if first else BAR)
        noise = rng.standard_normal(n) * (tt / (2 * BAR)) ** 2
        add(t0, noise, 0.12)
        steps = [0, 2] if first else [0, 1, 2, 3, 3.5, 3.75]
        for s in steps:
            kick(t0 + s * BEAT, 0.6 + s * 0.1)
        if not first:
            for i in range(16):
                snare(t0 + i * BEAT / 4, 0.1 + i * 0.04)
        pad(t0, Am if first else G, BAR, 0.06, 1800 if first else 2600)
    elif bar <= 30:  # party drop
        chord = PARTY[(bar - 27) % 4]
        pad(t0, chord, BAR + 0.2, 0.055, 3200)
        for b in range(4):
            kick(t0 + b * BEAT, 1.1)
            if b in (1, 3):
                snare(t0 + b * BEAT, 1.1)
            for h in range(4):
                hat(t0 + b * BEAT + h * BEAT / 4, 0.7 if h % 2 else 1.0, (-0.3, 0.3)[h % 2])
        root = chord[0] - 24
        for e in range(8):
            note(t0 + e * BEAT / 2, root + (12 if e % 2 else 0), BEAT / 2 * 0.85, 'saw', 0.12)
        for i in range(16):
            note(t0 + i * BEAT / 4, chord[(i * 2) % 3] + 24 + (12 if i % 8 == 7 else 0), BEAT / 4 * 0.7, 'pulse', 0.04,
                 (-0.6, 0.6)[i % 2])
        for beat, semi, length in MOTIF[(bar + 1) % 4]:
            note(t0 + beat * BEAT, chord[0] + semi, length * BEAT * 0.92, 'square', 0.065, 0.0, 0.01, 0.08, 0.006)
    else:  # outro: one big chord and a bell
        if bar == 31:
            kick(t0, 1.2)
            pad(t0, [48, 60, 64, 67, 72], BAR * 2, 0.2, 2400)
            note(t0, 36, BAR * 1.5, 'tri', 0.3, attack=0.01, release=1.2)
            for i, m in enumerate([72, 76, 79, 84]):
                bell(t0 + i * BEAT / 2, m, 0.18, (-0.4, 0.4)[i % 2])

# a little room: two short echoes
mix = np.stack([left, right], axis=1)
for delay, gain in ((0.11, 0.18), (0.23, 0.1)):
    d = int(delay * SR)
    mix[d:, 0] += mix[:-d, 1] * gain
    mix[d:, 1] += mix[:-d, 0] * gain
mix = np.tanh(mix * 1.4) * 0.85
fade = int(1.5 * SR)
end = int(LENGTH * SR)
mix = mix[:end]
mix[-fade:] *= np.linspace(1, 0, fade)[:, None]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
sf.write(OUT, mix.astype('float32'), SR)
print('music', round(len(mix) / SR, 1), 's ->', os.path.abspath(OUT))
