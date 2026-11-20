"""Cuts the recorded shots to the music and renders the trailer (1920x1080, 30 fps, H.264 + AAC).

Every cut lands on a bar of the 120 BPM music (2 s), and the sections match tools/trailer/music.py:
intro 0-10, work 10-44, dusk 44-50, cave 50-54, party 54-62, logo 62-66.

usage: python tools/trailer/edit.py   (needs runs/trailer/out/shots/*.mp4 and music.wav; FFMPEG may point at ffmpeg.exe)
"""
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'runs', 'trailer', 'out')
FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')
LOGO = os.path.join(ROOT, 'docs', 'logos', 'bubie-512.png')
FONT = 'C\\:/Windows/Fonts/segoeuib.ttf'

# shot, seconds into the clip to start from, seconds on screen, speed (1 = as recorded)
CUTS = [
    ('01_arrive', 0.0, 6.0, 1.0),
    ('02_hello', 0.0, 4.0, 1.0),
    ('03_lumber', 0.0, 4.0, 1.0),
    ('04_mine', 0.0, 7.0, 1.0),
    ('05_courier', 0.0, 7.0, 1.0),
    ('06_build', 0.0, 8.5, 1.0),
    ('07_crew', 0.0, 7.5, 0.6),
    ('08_dusk', 0.0, 6.0, 1.0),
    ('09_cave', 1.2, 4.0, 1.0),
    ('10_party', 0.0, 8.0, 1.0),
]
LOGO_SECONDS = 4.0


def main():
    args = [FFMPEG, '-y', '-loglevel', 'error']
    graph = []
    labels = []
    for i, (name, start, length, speed) in enumerate(CUTS):
        args += ['-i', os.path.join(OUT, 'shots', name + '.mp4')]
        # trim, slow down if asked, hold the last frame if the clip is short, then 16:9 at 1920x1080
        graph.append(
            f'[{i}:v]trim=start={start},setpts=(PTS-STARTPTS)/{speed},'
            f'tpad=stop_mode=clone:stop_duration={length},trim=duration={length},'
            f'crop=min(iw\\,ih*16/9):min(ih\\,iw*9/16),scale=1920:1080:flags=lanczos,fps=30,setsar=1,'
            f'eq=saturation=1.08:contrast=1.03[v{i}]')
        labels.append(f'[v{i}]')
    music = len(CUTS)
    args += ['-i', os.path.join(OUT, 'music.wav')]
    logo = music + 1
    args += ['-loop', '1', '-t', str(LOGO_SECONDS), '-i', LOGO]
    # the end card: dark screen, the logo fades in, a line under it
    graph.append(
        f'color=c=0x17141c:s=1920x1080:r=30:d={LOGO_SECONDS}[bg];'
        f'[{logo}:v]scale=560:560:flags=lanczos,format=rgba,fade=in:st=0.2:d=0.8:alpha=1[lg];'
        f'[bg][lg]overlay=(W-w)/2:(H-h)/2-70,'
        f"drawtext=fontfile='{FONT}':text='Bubie':fontsize=96:fontcolor=0xF4BF69:x=(w-text_w)/2:y=h/2+230:"
        f'alpha=\'if(lt(t,0.6),0,min(1,(t-0.6)/0.6))\','
        f"drawtext=fontfile='{FONT}':text='NeoForge 1.21.1  |  CurseForge':fontsize=40:fontcolor=0xEDE3D2:"
        f'x=(w-text_w)/2:y=h/2+345:alpha=\'if(lt(t,1.0),0,min(1,(t-1.0)/0.6))\',setsar=1[end]')
    labels.append('[end]')
    graph.append(''.join(labels) + f'concat=n={len(labels)}:v=1:a=0,fade=in:st=0:d=0.8,'
                 'format=yuv420p[video]')
    total = sum(c[2] for c in CUTS) + LOGO_SECONDS
    graph.append(f'[{music}:a]atrim=duration={total},afade=out:st={total - 1.5}:d=1.5[audio]')
    out = os.path.join(OUT, 'bubie-trailer.mp4')
    args += ['-filter_complex', ';'.join(graph), '-map', '[video]', '-map', '[audio]',
             '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-r', '30',
             '-c:a', 'aac', '-b:a', '320k', '-movflags', '+faststart', out]
    subprocess.run(args, check=True)
    print('trailer', round(total, 1), 's ->', os.path.abspath(out))


main()
