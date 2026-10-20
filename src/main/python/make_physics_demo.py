"""Builds dist/demo_physics: a small player model with a cloth hair chain and skirt chain."""
import base64
import io
import json
import os
import uuid
from PIL import Image, ImageDraw

ROOT = r"D:\ModDev"
SOURCE = os.path.join(ROOT, r"src\main\resources\assets\playermodel\example_small.bbmodel")
OUT = os.path.join(ROOT, r"dist\demo_physics")
os.makedirs(OUT, exist_ok=True)


def uid():
    return str(uuid.uuid4())


def cube(name, frm, to, uv_rect):
    return {
        "name": name,
        "uuid": uid(),
        "type": "cube",
        "from": frm,
        "to": to,
        "origin": frm,
        "rotation": [0, 0, 0],
        "inflate": 0.0,
        "mirror_uv": False,
        "box_uv": False,
        "uv_offset": [0, 0],
        "visibility": True,
        "export": True,
        "faces": {
            key: {"uv": uv_rect, "rotation": 0, "texture": 0}
            for key in ("north", "east", "south", "west", "up", "down")
        },
    }


def group(name, origin, children):
    return {
        "uuid": uid(),
        "name": name,
        "origin": origin,
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


def find_group(outliner, name):
    for item in outliner:
        if isinstance(item, dict):
            if item.get("name") == name:
                return item
            found = find_group(item.get("children", []), name)
            if found:
                return found
    return None


with open(SOURCE, "r", encoding="utf-8") as f:
    model = json.load(f)

# Recolor two unused 8x8 texture regions for hair and skirt.
texture = model["textures"][0]
raw = base64.b64decode(texture["source"].split(",", 1)[1])
image = Image.open(io.BytesIO(raw)).convert("RGBA")
draw = ImageDraw.Draw(image)
draw.rectangle([40, 0, 47, 7], fill=(190, 120, 70, 255))
draw.rectangle([48, 0, 55, 7], fill=(55, 190, 115, 255))
out = io.BytesIO()
image.save(out, format="PNG")
texture["source"] = "data:image/png;base64," + base64.b64encode(out.getvalue()).decode()
texture["name"] = "demo_physics"

hair_root_cube = cube("hair_root", [-3.2, 19.5, 2.6], [3.2, 23.1, 5.0], [40, 0, 48, 8])
hair_tip_cube = cube("hair_tip", [-2.8, 15.5, 3.0], [2.8, 19.5, 5.2], [40, 0, 48, 8])
skirt_root_cube = cube("skirt_root", [-3.8, 7.0, -3.4], [3.8, 12.0, 3.4], [48, 0, 56, 8])
skirt_tip_cube = cube("skirt_tip", [-4.2, 3.0, -3.8], [4.2, 7.0, 3.8], [48, 0, 56, 8])
model["elements"].extend([hair_root_cube, hair_tip_cube, skirt_root_cube, skirt_tip_cube])

hair_tip = group("hair_tip_physics", [0, 19.5, 3.6], [hair_tip_cube["uuid"]])
hair_root = group("hair_physics", [0, 23.1, 3.0],
                  [hair_root_cube["uuid"], hair_tip])
skirt_tip = group("skirt_tip_physics", [0, 7.0, 0],
                  [skirt_tip_cube["uuid"]])
skirt_root = group("skirt_physics", [0, 12.0, 0],
                   [skirt_root_cube["uuid"], skirt_tip])

head = find_group(model["outliner"], "head")
body = find_group(model["outliner"], "body")
if head is None or body is None:
    raise SystemExit("example_small must contain head/body groups")
head["children"].append(hair_root)
body["children"].append(skirt_root)
model["name"] = "demo_physics"

with open(os.path.join(OUT, "model.bbmodel"), "w", encoding="utf-8") as f:
    json.dump(model, f, ensure_ascii=False, indent=1)

meta = {
    "displayName": "物理演示：头发 + 裙摆",
    "textureMode": "model",
    "pivotMode": "model",
    "textureWidth": 64,
    "textureHeight": 64,
    "customAnchors": False,
    "physics": {
        "defaultSettings": {
            "mode": "pendulum",
            "stiffness": 90,
            "damping": 12,
            "gravity": 0.6,
            "inertia": 1.0,
            "maxAngle": 60
        },
        "bones": {
            "hair_physics": {
                "mode": "cloth",
                "stiffness": 75,
                "damping": 11,
                "gravity": 0.7,
                "inertia": 1.2,
                "maxAngle": 75
            },
            "skirt_physics": {
                "mode": "cloth",
                "stiffness": 60,
                "damping": 11,
                "gravity": 0.4,
                "inertia": 1.1,
                "maxAngle": 65
            }
        }
    }
}
with open(os.path.join(OUT, "meta.json"), "w", encoding="utf-8") as f:
    json.dump(meta, f, ensure_ascii=False, indent=2)

with open(os.path.join(OUT, "texture.png"), "wb") as f:
    f.write(out.getvalue())

with open(os.path.join(OUT, "README.txt"), "w", encoding="utf-8") as f:
    f.write(
        "物理演示模型\n"
        "1. 把整个 demo_physics 文件夹复制到 config/playermodel/models/ 下。\n"
        "2. /pmodel reload\n"
        "3. /pmodel use demo_physics\n"
        "头发和裙摆都是两段 _physics 链，使用 cloth 模式。\n"
    )

print("written", OUT)
