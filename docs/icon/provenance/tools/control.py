"""Interact only with the owned :205 display after verifying its audio sink."""
from pathlib import Path
import os, subprocess, sys, time
D = Path(__file__).resolve().parents[1]
E = dict(os.environ, DISPLAY=':205')
sinks = subprocess.check_output(['pactl', 'list', 'short', 'sinks'], text=True)
sink_id = next(line.split()[0] for line in sinks.splitlines() if '\tround3_dimension5\t' in line)
streams = subprocess.check_output(['pactl', 'list', 'short', 'sink-inputs'], text=True)
assert any(line.split()[1] == sink_id for line in streams.splitlines()), 'Client not routed to owned null sink'
(D / 'evidence/audio-routing-latest.txt').write_text(sinks + '\nSINK INPUTS\n' + streams)
def xd(*args):
    subprocess.run(['xdotool', *args], env=E, check=True)
windows = subprocess.check_output(['xdotool', 'search', '--name', 'Minecraft'], env=E, text=True).split()
assert windows
xd('windowfocus', windows[-1])
def key(name):
    xd('keydown', name)
    time.sleep(0.35)
    xd('keyup', name)
    time.sleep(0.5)
mode = sys.argv[1]
if mode == 'chat':
    key('t')
    time.sleep(0.6)
    xd('key', 'ctrl+a')
    xd('type', '--clearmodifiers', '--delay', '12', sys.argv[2])
    key('Return')
    with (D / 'evidence/commands.txt').open('a') as out:
        out.write(time.strftime('%Y-%m-%dT%H:%M:%S') + ' ' + sys.argv[2] + '\n')
elif mode == 'key':
    key(sys.argv[2])
elif mode == 'type':
    xd('type', '--clearmodifiers', '--delay', '20', sys.argv[2])
elif mode == 'shot':
    from PIL import ImageGrab
    ImageGrab.grab(xdisplay=':205').save(D / sys.argv[2])
elif mode == 'click':
    xd('mousemove', sys.argv[2], sys.argv[3])
    xd('mousedown', '1')
    time.sleep(0.25)
    xd('mouseup', '1')
    time.sleep(0.8)
