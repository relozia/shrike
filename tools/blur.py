# writes the hud_blur_N.json post chains. run from the repo root:
#   python tools/blur.py
# minecraft loads a post chain by resource id and bakes the uniforms into the json, there's no way to hand it a
# radius at draw time, so every step the HUD Blur slider can land on needs its own file. hence 16 near identical
# ones instead of a loop. keep the crlf and the missing trailing newline or every file shows up dirty in git.
import json

OUT = "src/main/resources/assets/shrike/post_effect/"
STEPS = 16


# box blur twice in each direction, h v h v. two rounds is close enough to a gaussian and still cheap
def passes(r):
    out = []
    for i in range(4):
        horizontal = i % 2 == 0
        out.append({
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "minecraft:post/box_blur",
            "inputs": [{"sampler_name": "In", "target": "minecraft:main" if horizontal else "swap", "bilinear": True}],
            "output": "swap" if horizontal else "minecraft:main",
            "uniforms": {"BlurConfig": [
                {"name": "BlurDir", "type": "vec2", "value": [1.0, 0.0] if horizontal else [0.0, 1.0]},
                {"name": "Radius", "type": "float", "value": float(r)},
            ]},
        })
    return out


if __name__ == "__main__":
    for r in range(1, STEPS + 1):
        text = json.dumps({"targets": {"swap": {}}, "passes": passes(r)}, indent=1)
        with open(OUT + "hud_blur_%d.json" % r, "w", newline="\r\n") as f:
            f.write(text)
    print("  -> %d steps" % STEPS)
