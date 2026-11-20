"""Vertical cut of the trailer (1080x1920) for Shorts, TikTok and Reels, about 24 s.

Two pieces of the score, each cut on a bar: 6-18 s (the morning bells into the groove) under the day shots, then
52-66 s (the rise, the party drop and the last chord) under the cave, the party and the end card.

usage: python tools/trailer/vertical.py   (after the shots are recorded; FFMPEG may point at ffmpeg.exe)
"""
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'runs', 'trailer', 'out')
FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')
LOGO = os.path.join(ROOT, 'docs', 'logos', 'bubie-512.png')
FONT = 'C\\:/Windows/Fonts/segoeuib.ttf'

# shot, start, seconds, horizontal centre of the 9:16 window (0 = left edge, 0.5 = middle, 1 = right edge)
CUTS = [
    ('02_hello', 0.0, 4.0, 0.47),
    ('03_lumber', 0.0, 2.0, 0.42),
    ('07_crew', 0.6, 4.0, 0.50),
    ('05_courier', 0.0, 2.0, 0.28),
    ('09_cave', 2.0, 2.0, 0.50),
    ('10_party', 0.0, 8.0, 0.48),
]
LOGO_SECONDS = 4.0
MUSIC = [(6.0, 12.0), (52.0, 14.0)]  # (start in music.wav, seconds)


def main():
    args = [FFMPEG, '-y', '-loglevel', 'error']
    graph, labels = [], []
    for i, (name, start, length, centre) in enumerate(CUTS):
        args += ['-i', os.path.join(OUT, 'shots', name + '.mp4')]
        graph.append(
            f'[{i}:v]trim=start={start},setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration={length},'
            f'trim=duration={length},crop=ih*9/16:ih:(iw-ih*9/16)*{centre}:0,'
            f'scale=1080:1920:flags=lanczos,fps=30,setsar=1,eq=saturation=1.1:contrast=1.04[v{i}]')
        labels.append(f'[v{i}]')
    music = len(CUTS)
    args += ['-i', os.path.join(OUT, 'music.wav')]
    logo = music + 1
    args += ['-loop', '1', '-t', str(LOGO_SECONDS), '-i', LOGO]
    graph.append(
        f'color=c=0x17141c:s=1080x1920:r=30:d={LOGO_SECONDS}[bg];'
        f'[{logo}:v]scale=720:720:flags=lanczos,format=rgba,fade=in:st=0.2:d=0.8:alpha=1[lg];'
        f'[bg][lg]overlay=(W-w)/2:(H-h)/2-160,'
        f"drawtext=fontfile='{FONT}':text='Bubie':fontsize=120:fontcolor=0xF4BF69:x=(w-text_w)/2:y=h/2+260:"
        f'alpha=\'if(lt(t,0.6),0,min(1,(t-0.6)/0.6))\','
        f"drawtext=fontfile='{FONT}':text='Minecraft mod  |  NeoForge 1.21.1':fontsize=48:fontcolor=0xEDE3D2:"
        f'x=(w-text_w)/2:y=h/2+410:alpha=\'if(lt(t,1.0),0,min(1,(t-1.0)/0.6))\','
        f"drawtext=fontfile='{FONT}':text='on CurseForge':fontsize=48:fontcolor=0xEDE3D2:"
        f'x=(w-text_w)/2:y=h/2+480:alpha=\'if(lt(t,1.0),0,min(1,(t-1.0)/0.6))\',setsar=1[end]')
    labels.append('[end]')
    graph.append(''.join(labels) + f'concat=n={len(labels)}:v=1:a=0,fade=in:st=0:d=0.5,format=yuv420p[video]')
    (a0, l0), (a1, l1) = MUSIC
    graph.append(f'[{music}:a]asplit=2[m0][m1];'
                 f'[m0]atrim=start={a0}:duration={l0 + 0.15},asetpts=PTS-STARTPTS[p0];'
                 f'[m1]atrim=start={a1 - 0.15}:duration={l1 + 0.15},asetpts=PTS-STARTPTS[p1];'
                 f'[p0][p1]acrossfade=d=0.3,afade=out:st={l0 + l1 - 1.2}:d=1.2[audio]')
    out = os.path.join(OUT, 'bubie-trailer-vertical.mp4')
    args += ['-filter_complex', ';'.join(graph), '-map', '[video]', '-map', '[audio]',
             '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-r', '30',
             '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', '-shortest', out]
    subprocess.run(args, check=True)
    print('vertical', sum(c[2] for c in CUTS) + LOGO_SECONDS, 's ->', os.path.abspath(out))


main()
