import base64
import io
import json
import os
import uuid as uuidlib
from PIL import Image, ImageDraw

OUT = r"D:\ModDev\src\main\resources\assets\playermodel"
os.makedirs(OUT, exist_ok=True)


def make_texture(size=64):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, size - 1], fill=(232, 190, 150, 255))
    d.rectangle([16, 16, 40, 32], fill=(60, 120, 200, 255))
    d.rectangle([0, 16, 16, 32], fill=(90, 90, 110, 255))
    d.rectangle([0, 32, 16, 48], fill=(70, 70, 90, 255))
    d.rectangle([40, 16, 56, 32], fill=(232, 190, 150, 255))
    d.rectangle([32, 48, 48, 64], fill=(232, 190, 150, 255))
    d.rectangle([16, 48, 32, 64], fill=(40, 60, 150, 255))
    d.rectangle([8, 8, 16, 16], fill=(240, 200, 160, 255))
    d.rectangle([10, 11, 11, 13], fill=(40, 30, 30, 255))
    d.rectangle([13, 11, 14, 13], fill=(40, 30, 30, 255))
    d.rectangle([11, 14, 13, 14], fill=(150, 60, 60, 255))
    d.rectangle([8, 8, 16, 9], fill=(50, 35, 25, 255))
    d.rectangle([8, 8, 9, 12], fill=(50, 35, 25, 255))
    d.rectangle([16, 28, 40, 30], fill=(30, 30, 30, 255))
    out = io.BytesIO()
    img.save(out, format="PNG")
    return out.getvalue(), img


def data_uri(raw):
    return "data:image/png;base64," + base64.b64encode(raw).decode()


def uid():
    return str(uuidlib.uuid4())


def box_uv_faces(u, v, dx, dy, dz):
    j = u
    k = u + dz
    l = u + dz + dx
    m = u + dz + dx + dx
    n = u + dz + dx + dz
    o = u + dz + dx + dz + dx
    p = v
    q = v + dz
    r = v + dz + dy
    return {
        "down": [k, p, l, q],
        "up": [l, q, m, p],
        "west": [j, q, k, r],
        "north": [k, q, l, r],
        "east": [l, q, n, r],
        "south": [n, q, o, r],
    }


def element(name, frm, to, uv=None, faces=None, box_uv=True, inflate=0.0):
    data = {
        "name": name,
        "uuid": uid(),
        "type": "cube",
        "from": [round(v, 4) for v in frm],
        "to": [round(v, 4) for v in to],
        "origin": [round(v, 4) for v in frm],
        "rotation": [0, 0, 0],
        "inflate": inflate,
        "mirror_uv": False,
        "box_uv": box_uv,
        "uv_offset": [uv[0], uv[1]] if uv else [0, 0],
        "visibility": True,
        "export": True,
        "faces": {},
    }
    if faces:
        for key, rect in faces.items():
            data["faces"][key] = {"uv": rect, "rotation": 0, "texture": 0}
    else:
        for key in ("north", "east", "south", "west", "up", "down"):
            data["faces"][key] = {"uv": [0, 0, 4, 4], "rotation": 0, "texture": 0}
    return data


def group(name, origin, children):
    return {
        "uuid": uid(),
        "name": name,
        "origin": [round(v, 4) for v in origin],
        "rotation": [0, 0, 0],
        "color": 0,
        "mirror_uv": False,
        "isOpen": True,
        "locked": False,
        "visibility": True,
        "autouv": 0,
        "export": True,
        "children": children,
    }



def add_anchors(out, els, scale=1.0, bb_space=True):
    # All templates are authored in Blockbench Y-up space (feet y=0, head y=24).
    entries = [
        ("anchor_helmet", (0, 24 * scale, 0)),
        ("anchor_chest", (0, 24 * scale, 0)),
        ("anchor_legs", (0, 24 * scale, 0)),
        ("anchor_boots", (0, 12 * scale, 0)),
        ("anchor_right_hand", (-5 * scale, 22 * scale, 0)),
        ("anchor_left_hand", (5 * scale, 22 * scale, 0)),
        ("anchor_cape", (0, 24 * scale, -2)),
    ]
    for name, origin in entries:
        r = 2.0 if name == "anchor_cape" else 2.0 * scale
        marker = element(name + "_size", [origin[0] - r, origin[1] - r, origin[2] - r],
                         [origin[0] + r, origin[1] + r, origin[2] + r], None, box_uv=False)
        els.append(marker)
        out.append(group(name, origin, [marker["uuid"]]))

LAYERS = [
    ("head", "hat", (32, 0), 0.5),
    ("body", "jacket", (16, 32), 0.25),
    ("right_arm", "right_sleeve", (40, 32), 0.25),
    ("left_arm", "left_sleeve", (48, 48), 0.25),
    ("right_leg", "right_pants", (0, 32), 0.25),
    ("left_leg", "left_pants", (16, 48), 0.25),
]


def add_layers(els, out, origins, scale=1.0):
    base_by_name = {e["name"]: e for e in els}
    for base_name, layer_name, uv, inflate in LAYERS:
        base = base_by_name.get(base_name)
        if base is None:
            continue
        e = element(layer_name, base["from"], base["to"], uv, box_uv=True, inflate=inflate * scale)
        els.append(e)
        out.append(group(layer_name, origins[base_name], [e["uuid"]]))


def model(elements, outliner, tex_uri, box_uv, name):

    return {
        "meta": {
            "format_version": "4.9",
            "model_format": "free",
            "box_uv": box_uv,
        },
        "name": name,
        "resolution": {"width": 64, "height": 64},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "name": "skin.png",
            "id": 0,
            "source": tex_uri,
            "uv_width": 64,
            "uv_height": 64,
        }],
    }


tex, img = make_texture()
_d = ImageDraw.Draw(img)
for _rect in [[32, 0, 64, 16], [16, 32, 40, 48], [40, 32, 56, 48],
              [48, 48, 64, 64], [0, 32, 16, 48], [16, 48, 32, 64]]:
    _d.rectangle(_rect, fill=(0, 0, 0, 0))
tex_uri = data_uri(tex)
img.save(os.path.join(OUT, "template_texture.png"))

parts = [
    ("head", [0, 24, 0], [-4, 24, -4, 4, 32, 4], (0, 0), 8, 8, 8),
    ("body", [0, 24, 0], [-4, 12, -2, 4, 24, 2], (16, 16), 8, 12, 4),
    ("right_arm", [-5, 22, 0], [-8, 12, -2, -4, 24, 2], (40, 16), 4, 12, 4),
    ("left_arm", [5, 22, 0], [4, 12, -2, 8, 24, 2], (32, 48), 4, 12, 4),
    ("right_leg", [-1.9, 12, 0], [-3.9, 0, -2, 0.1, 12, 2], (0, 16), 4, 12, 4),
    ("left_leg", [1.9, 12, 0], [-0.1, 0, -2, 3.9, 12, 2], (16, 48), 4, 12, 4),
]
els, groups, out = [], [], []
for pname, origin, b, uv, dx, dy, dz in parts:
    e = element(pname, b[:3], b[3:], uv)
    els.append(e)
    out.append(group(pname, origin, [e["uuid"]]))
add_anchors(out, els, 1.0, True)
with open(os.path.join(OUT, "template.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model(els, out, tex_uri, True, "player_rig_template"), f, ensure_ascii=False, indent=1)

slim_parts = [
    ("head", [0, 24, 0], [-4, 24, -4, 4, 32, 4], (0, 0), 8, 8, 8),
    ("body", [0, 24, 0], [-4, 12, -2, 4, 24, 2], (16, 16), 8, 12, 4),
    ("right_arm", [-5, 21.5, 0], [-7, 12, -2, -4, 24, 2], (40, 16), 3, 12, 4),
    ("left_arm", [5, 21.5, 0], [4, 12, -2, 7, 24, 2], (32, 48), 3, 12, 4),
    ("right_leg", [-1.9, 12, 0], [-3.9, 0, -2, 0.1, 12, 2], (0, 16), 4, 12, 4),
    ("left_leg", [1.9, 12, 0], [-0.1, 0, -2, 3.9, 12, 2], (16, 48), 4, 12, 4),
]
els, groups, out = [], [], []
for pname, origin, b, uv, dx, dy, dz in slim_parts:
    e = element(pname, b[:3], b[3:], uv)
    els.append(e)
    out.append(group(pname, origin, [e["uuid"]]))
add_anchors(out, els, 1.0, True)
with open(os.path.join(OUT, "template_slim.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model(els, out, tex_uri, True, "player_rig_template_slim"), f, ensure_ascii=False, indent=1)

s = 0.6
small_parts = [
    ("head", [0, 24 * s, 0], [-4 * s, 24 * s, -4 * s, 4 * s, 32 * s, 4 * s], (0, 0), 8, 8, 8),
    ("body", [0, 24 * s, 0], [-4 * s, 12 * s, -2 * s, 4 * s, 24 * s, 2 * s], (16, 16), 8, 12, 4),
    ("right_arm", [-5 * s, 22 * s, 0], [-8 * s, 12 * s, -2 * s, -4 * s, 24 * s, 2 * s], (40, 16), 4, 12, 4),
    ("left_arm", [5 * s, 22 * s, 0], [4 * s, 12 * s, -2 * s, 8 * s, 24 * s, 2 * s], (32, 48), 4, 12, 4),
    ("right_leg", [-1.9 * s, 12 * s, 0], [-3.9 * s, 0, -2 * s, 0.1 * s, 12 * s, 2 * s], (0, 16), 4, 12, 4),
    ("left_leg", [1.9 * s, 12 * s, 0], [-0.1 * s, 0, -2 * s, 3.9 * s, 12 * s, 2 * s], (16, 48), 4, 12, 4),
]
els, groups, out = [], [], []
for pname, origin, b, uv, dx, dy, dz in small_parts:
    faces = box_uv_faces(uv[0], uv[1], dx, dy, dz)
    e = element(pname, b[:3], b[3:], uv, faces=faces, box_uv=False)
    els.append(e)
    out.append(group(pname, origin, [e["uuid"]]))
add_anchors(out, els, s, True)
with open(os.path.join(OUT, "template_small.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model(els, out, tex_uri, False, "player_rig_template_small"), f, ensure_ascii=False, indent=1)

s = 0.7
demo_parts = [
    ("head", [0, 24 * s, 0], [-4.5 * s, 24 * s, -4.5 * s, 4.5 * s, 33 * s, 4.5 * s], (0, 0), 8, 8, 8),
    ("body", [0, 24 * s, 0], [-3.5 * s, 12 * s, -2 * s, 3.5 * s, 24 * s, 2 * s], (16, 16), 8, 12, 4),
    ("right_arm", [-5 * s, 22 * s, 0], [-8 * s, 12 * s, -2 * s, -4 * s, 24 * s, 2 * s], (40, 16), 4, 12, 4),
    ("left_arm", [5 * s, 22 * s, 0], [4 * s, 12 * s, -2 * s, 8 * s, 24 * s, 2 * s], (32, 48), 4, 12, 4),
    ("right_leg", [-1.9 * s, 12 * s, 0], [-3.9 * s, 0, -2 * s, 0.1 * s, 12 * s, 2 * s], (0, 16), 4, 12, 4),
    ("left_leg", [1.9 * s, 12 * s, 0], [-0.1 * s, 0, -2 * s, 3.9 * s, 12 * s, 2 * s], (16, 48), 4, 12, 4),
]
els, groups, out = [], [], []
for pname, origin, b, uv, dx, dy, dz in demo_parts:
    faces = box_uv_faces(uv[0], uv[1], dx, dy, dz)
    e = element(pname, b[:3], b[3:], uv, faces=faces, box_uv=False)
    els.append(e)
    out.append(group(pname, origin, [e["uuid"]]))
head_top = 33 * s
antenna = element("antenna", [-1.2, head_top, -1.2], [1.2, head_top + 4, 1.2], None, box_uv=False)
tip = element("antenna_tip", [-1.8, head_top + 4, -1.8], [1.8, head_top + 6, 1.8], None, box_uv=False)
backpack = element("backpack", [-3 * s, 13 * s, -4.4 * s], [3 * s, 22 * s, -2 * s], None, box_uv=False)
els.extend([antenna, tip, backpack])
out[0]["children"].extend([antenna["uuid"], tip["uuid"]])
out[1]["children"].append(backpack["uuid"])
add_anchors(out, els, s, True)
with open(os.path.join(OUT, "example_small.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model(els, out, tex_uri, False, "example_small"), f, ensure_ascii=False, indent=1)

icon = Image.new("RGBA", (128, 128), (26, 30, 40, 255))
d = ImageDraw.Draw(icon)
d.rounded_rectangle([16, 16, 112, 112], radius=20, fill=(40, 48, 64, 255), outline=(90, 200, 255, 255), width=4)
d.rectangle([40, 30, 88, 78], fill=(232, 190, 150, 255), outline=(0, 0, 0, 255), width=3)
d.rectangle([50, 46, 58, 54], fill=(40, 30, 30, 255))
d.rectangle([70, 46, 78, 54], fill=(40, 30, 30, 255))
d.rectangle([56, 64, 72, 68], fill=(150, 60, 60, 255))
d.rectangle([34, 82, 94, 108], fill=(60, 120, 200, 255), outline=(0, 0, 0, 255), width=3)
icon.save(os.path.join(OUT, "icon.png"))
print("assets written", OUT)
