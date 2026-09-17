"""Read-only scan of natural structure starts, including exact bounding boxes."""
from pathlib import Path
import json, zlib, io, nbtlib
D = Path(__file__).resolve().parents[1]
world = D / 'runtime/saves/Dimension Natural 4'
found = []
errors = []
for region in world.glob('dimensions/minecraft/*/region/*.mca'):
    raw = region.read_bytes()
    for slot in range(1024):
        offset = int.from_bytes(raw[slot*4:slot*4+3], 'big') * 4096
        if not offset:
            continue
        length = int.from_bytes(raw[offset:offset+4], 'big')
        if raw[offset+4] != 2:
            errors.append({'region':str(region),'slot':slot,'compression':raw[offset+4]})
            continue
        try:
            chunk = nbtlib.File.parse(io.BytesIO(zlib.decompress(raw[offset+5:offset+4+length]))).unpack()
            for key, start in chunk.get('structures',{}).get('starts',{}).items():
                if not key.startswith('dimensionstructurevariants:') or start.get('id') == 'INVALID':
                    continue
                boxes = [list(map(int, child['BB'])) for child in start.get('Children',[])]
                if boxes:
                    bounds = [min(b[i] for b in boxes) for i in range(3)] + [max(b[i] for b in boxes) for i in range(3,6)]
                    found.append({'id':key,'dimension':region.parents[1].name,'chunk':[int(chunk['xPos']),int(chunk['zPos'])], 'box':bounds,'pieces':len(boxes),'status':chunk['Status'],'region':str(region.relative_to(D))})
        except Exception as exc:
            errors.append({'region':str(region.relative_to(D)),'slot':slot,'error':repr(exc)})
worldgen = nbtlib.load(world / 'data/minecraft/world_gen_settings.dat').unpack()
report = {'worldgen':worldgen,'structures':found,'errors':errors}
(D / 'evidence/natural-structure-starts.json').write_text(json.dumps(report,indent=2))
print(json.dumps({'worldgen':worldgen,'structureCount':len(found),'errors':errors},indent=2))
