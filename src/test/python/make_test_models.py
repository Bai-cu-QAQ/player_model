import base64
import json
import os
from PIL import Image, ImageDraw

ROOT = r"D:\ModDev\src\test\resources"
os.makedirs(ROOT, exist_ok=True)


def png_bytes(size=(64, 64)):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    palette = [
        (220, 80, 80, 255), (80, 160, 220, 255), (90, 200, 120, 255), (230, 200, 90, 255),
        (180, 110, 220, 255), (240, 140, 60, 255), (60, 200, 200, 255), (200, 200, 200, 255),
    ]
    for y in range(0, size[1], 8):
        for x in range(0, size[0], 8):
            c = palette[((x // 8) + (y // 8)) % len(palette)]
            d.rectangle([x, y, x + 7, y + 7], fill=c)
    d.rectangle([0, 0, size[0] - 1, size[1] - 1], outline=(0, 0, 0, 255))
    import io
    out = io.BytesIO()
    img.save(out, format="PNG")
    return out.getvalue()


def data_uri(raw):
    return "data:image/png;base64," + base64.b64encode(raw).decode()


def element(uuid, name, frm, to, uv, box_uv=True, inflate=0.0):
    faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        faces[face] = {"uv": [0, 0, 4, 4], "texture": 0}
    return {
        "name": name, "uuid": uuid, "type": "cube",
        "from": list(frm), "to": list(to), "origin": list(frm),
        "rotation": [0, 0, 0], "uv_offset": [uv[0], uv[1]],
        "box_uv": box_uv, "inflate": inflate, "mirror_uv": False, "faces": faces,
    }


def group(name, origin, children):
    return {"name": name, "origin": list(origin), "rotation": [0, 0, 0], "children": children}


tex = png_bytes()
tex_uri = data_uri(tex)

# 1. Full-size vanilla-space model.
elements = []
outliner = []
elements.append(element("head", "head", [-4, -8, -4], [4, 0, 4], (0, 0)))
outliner.append(group("head", [0, 0, 0], ["head"]))
elements.append(element("body", "body", [-4, 0, -2], [4, 12, 2], (16, 16)))
outliner.append(group("body", [0, 0, 0], ["body"]))
elements.append(element("rarm", "right_arm", [-8, 0, -2], [-4, 12, 2], (40, 16)))
outliner.append(group("right_arm", [-5, 2, 0], ["rarm"]))
elements.append(element("larm", "left_arm", [4, 0, -2], [8, 12, 2], (32, 48)))
outliner.append(group("left_arm", [5, 2, 0], ["larm"]))
elements.append(element("rleg", "right_leg", [-3.9, 12, -2], [0.1, 24, 2], (0, 16)))
outliner.append(group("right_leg", [-1.9, 12, 0], ["rleg"]))
elements.append(element("lleg", "left_leg", [-0.1, 12, -2], [3.9, 24, 2], (16, 48)))
outliner.append(group("left_leg", [1.9, 12, 0], ["lleg"]))

model = {
    "meta": {"format_version": 4, "model_format": "java_block", "box_uv": True},
    "name": "test_player", "resolution": {"width": 64, "height": 64},
    "elements": elements, "outliner": outliner,
    "textures": [{"name": "skin.png", "id": "0", "source": tex_uri}],
}
with open(os.path.join(ROOT, "player_vanilla.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model, f, ensure_ascii=False)

# 2. Small 0.6 rig in Blockbench space (feet y=0, head y=24*0.6).
s = 0.6
elements2 = []
outliner2 = []
elements2.append(element("head2", "head", [-4 * s, 24 * s, -4 * s], [4 * s, 32 * s, 4 * s], (0, 0), box_uv=False))
outliner2.append(group("head", [0, 24 * s, 0], ["head2"]))
elements2.append(element("body2", "body", [-4 * s, 12 * s, -2 * s], [4 * s, 24 * s, 2 * s], (16, 16), box_uv=False))
outliner2.append(group("body", [0, 24 * s, 0], ["body2"]))
elements2.append(element("rarm2", "right_arm", [-8 * s, 12 * s, -2 * s], [-4 * s, 24 * s, 2 * s], (40, 16), box_uv=False))
outliner2.append(group("right_arm", [-5 * s, 22 * s, 0], ["rarm2"]))
elements2.append(element("larm2", "left_arm", [4 * s, 12 * s, -2 * s], [8 * s, 24 * s, 2 * s], (32, 48), box_uv=False))
outliner2.append(group("left_arm", [5 * s, 22 * s, 0], ["larm2"]))
elements2.append(element("rleg2", "right_leg", [-3.9 * s, 0, -2 * s], [0.1 * s, 12 * s, 2 * s], (0, 16), box_uv=False))
outliner2.append(group("right_leg", [-1.9 * s, 12 * s, 0], ["rleg2"]))
elements2.append(element("lleg2", "left_leg", [-0.1 * s, 0, -2 * s], [3.9 * s, 12 * s, 2 * s], (16, 48), box_uv=False))
outliner2.append(group("left_leg", [1.9 * s, 12 * s, 0], ["lleg2"]))

model2 = {
    "meta": {"format_version": 4, "model_format": "java_block", "box_uv": False},
    "name": "test_small", "resolution": {"width": 64, "height": 64},
    "elements": elements2, "outliner": outliner2,
    "textures": [{"name": "skin.png", "id": "0", "source": tex_uri}],
}
with open(os.path.join(ROOT, "player_small.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model2, f, ensure_ascii=False)

print("written", ROOT)
