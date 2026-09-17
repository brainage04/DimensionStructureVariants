"""Lossless rectangular crops centred on visually annotated complete city extents."""
from pathlib import Path
from PIL import Image, ImageDraw
import hashlib, json

D = Path(__file__).resolve().parents[1]
selections = json.loads((D / 'capture-selections.json').read_text())
starts = json.loads((D / 'evidence/natural-structure-starts.json').read_text())
manifest = []
proof = []
for selection in selections:
    source = D / 'runtime/screenshots' / selection['raw']
    original = Image.open(source).convert('RGB')
    x0, y0, x1, y1 = selection['sourceBoundingBoxXYXY']
    width, height = selection['outputSize']
    left, top = round((x0 + x1 - width) / 2), round((y0 + y1 - height) / 2)
    crop_box = [left, top, left + width, top + height]
    assert 0 <= left < x0 < x1 < left + width <= original.width
    assert 0 <= top < y0 < y1 < top + height <= original.height
    result = original.crop(crop_box)
    destination = D / 'renders' / selection['file']
    result.save(destination)
    output_bbox = [x0-left, y0-top, x1-left, y1-top]
    bbox_centre = [(output_bbox[0]+output_bbox[2])/2, (output_bbox[1]+output_bbox[3])/2]
    offset = [bbox_centre[0]-width/2, bbox_centre[1]-height/2]
    margins = {'left':output_bbox[0], 'right':width-output_bbox[2], 'top':output_bbox[1], 'bottom':height-output_bbox[3]}
    assert max(map(abs,offset)) <= 0.5
    assert Image.open(destination).convert('RGB').tobytes() == original.crop(crop_box).tobytes()
    natural = next(s for s in starts['structures'] if s['id']=='dimensionstructurevariants:end_city_overworld' and s['chunk']==selection['chunk'])
    assert natural['status'] == 'minecraft:full'
    b = natural['box']
    centring = {'method':'Visually annotated complete visible End-city bounding rectangle, including its ship when present; excludes terrain, shadows and other structures. Pixel edge coordinates are half-open XYXY. Annotation uncertainty approximately ±3 pixels; reported offsets are exact relative to the annotated rectangle, not an opaque-pixel centroid.', 'sourceBoundingBoxXYXY':selection['sourceBoundingBoxXYXY'], 'sourceImageCentreXY':[original.width/2,original.height/2], 'sourceBoundingBoxCentreOffsetXY':[(x0+x1-original.width)/2,(y0+y1-original.height)/2], 'outputBoundingBoxXYXY':output_bbox, 'outputBoundingBoxCentreXY':bbox_centre, 'outputImageCentreXY':[width/2,height/2], 'offsetPixelsXY':offset, 'marginsPixels':margins, 'annotationUncertaintyPixels':3}
    manifest.append({'project':'DimensionStructureVariants','label':selection['label'],'path':str(destination.relative_to(D.parent)),'method':'Real Minecraft 26.2 + Fabric 0.19.3 + byte-identical approved DimensionStructureVariants build. Real natural world generation; native F2 screenshot with F1 HUD hidden; exact rectangular crop only. No shaders, resource packs, recolouring, compositing, /place or hand-building.','source':str(source.relative_to(D.parent))+'; captures-dimension5/runtime/saves/Dimension Natural 4; captures-dimension5/evidence/natural-structure-starts.json','notes':selection['notes'],'structureId':natural['id'],'structureType':'end_city','dimension':'minecraft:overworld','worldSeed':str(starts['worldgen']['data']['seed']),'worldName':'Dimension Natural 4','coordinates':{'structureStartXZ':[n*16 for n in natural['chunk']],'structureCentreXYZ':[(b[i]+b[i+3])/2 for i in range(3)],'pieceBoundingBox':b,'cameraXYZ':selection['cameraXYZ']},'cameraCommandExecuted':selection['cameraCommand'],'generation':'Read-only scan and one /locate query in an isolated byte-copy of the approved existing natural world. Visiting these additional starts completed normal chunk generation. Four distinct structures, none the approved original at chunk [-40,5]. No structure-placement or block-editing commands.','nightVision':False,'lighting':'Native daylight; /time set noon before this final F2 capture, clear weather, no night vision.','structurePieceCount':natural['pieces'],'naturalChunkStatus':natural['status'],'cropRectangleXYXY':crop_box,'dimensions':[width,height],'centring':centring,'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()})
    annotated = result.copy()
    draw = ImageDraw.Draw(annotated)
    draw.rectangle(output_bbox,outline=(255,70,70),width=2)
    draw.line((width/2-15,height/2,width/2+15,height/2),fill='yellow',width=2)
    draw.line((width/2,height/2-15,width/2,height/2+15),fill='yellow',width=2)
    annotated.save(D / 'evidence' / ('measured-'+selection['file']))
    proof.append({'file':selection['file'],'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'resultSha256':manifest[-1]['sha256'],'exactPixelEqualityToRawCrop':True,'centreOffsetXY':offset,'margins':margins})
(D / 'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(D / 'evidence/crop-verification.json').write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps(proof,indent=2))
