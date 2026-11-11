# draws Kestrel's capes into vanilla's cape layout at 8x (512x256). needs Pillow. run from the repo root:
#   python tools/capes.py
# layout, in 64x32 cape pixels: the cape box is 10x16x1 at (0,0), its outside (what people behind you see) is
# x 1-11 y 1-17, the inside against your back x 12-22. the elytra box is 10x20x2 at (22,0) with its two big faces at
# x 24-34 and x 36-46 (y 2-22). from behind you see the second one, both get the design so it's right either way
import re
import time
from PIL import Image, ImageDraw

S = 8
OUT = "src/main/resources/assets/kestrel/textures/cape/"
RUST = (224, 112, 58)
COAL = (20, 24, 31)
INK = (13, 16, 21)


# path data and whether it's a cut-out (none use one right now), from the logo or one of the other marks in tools/marks
def paths(svg):
    return [(d, f == "#000") for d, f in re.findall(r'd="([^"]+)" fill="([^"]+)"', open(svg, encoding="utf-8").read())]


def flatten(d, steps=24):
    toks = re.findall(r"[MLCZ]|-?\d*\.?\d+", d)
    polys, cur, i, cmd, p = [], [], 0, None, (0, 0)
    def num():
        nonlocal i
        i += 1
        return float(toks[i - 1])
    while i < len(toks):
        if toks[i] in "MLCZ":
            cmd = toks[i]; i += 1
            if cmd == "Z":
                polys.append(cur); cur = []
                continue
        if cmd == "M":
            p = (num(), num()); cur = [p]; cmd = "L"
        elif cmd == "L":
            p = (num(), num()); cur.append(p)
        elif cmd == "C":
            c1, c2, e = (num(), num()), (num(), num()), (num(), num())
            for k in range(1, steps + 1):
                t = k / steps; u = 1 - t
                cur.append(tuple(u**3 * p[j] + 3 * u * u * t * c1[j] + 3 * u * t * t * c2[j] + t**3 * e[j] for j in range(2)))
            p = e
    return polys


# a mark into a box, supersampled so the edges stay smooth at 8x
def bird(img, x, y, size, color, svg="tools/logo.svg"):
    ss = 4
    m = Image.new("L", (int(size * ss), int(size * ss)), 0)
    dr = ImageDraw.Draw(m)
    k = size * ss / 100
    for d, cut in paths(svg):
        for poly in flatten(d):
            dr.polygon([(a * k, b * k) for a, b in poly], fill=0 if cut else 255)
    m = m.resize((int(size), int(size)), Image.LANCZOS)
    img.paste(Image.new("RGB", m.size, color), (int(x), int(y)), m)


def vgrad(img, box, top, bottom):
    x0, y0, x1, y1 = [v * S for v in box]
    dr = ImageDraw.Draw(img)
    for yy in range(y0, y1):
        t = (yy - y0) / max(1, y1 - y0 - 1)
        dr.line([(x0, yy), (x1 - 1, yy)], fill=tuple(round(top[j] + (bottom[j] - top[j]) * t) for j in range(3)))


def base(fill, inside):
    img = Image.new("RGB", (64 * S, 32 * S), fill)
    ImageDraw.Draw(img).rectangle([12 * S, 1 * S, 22 * S - 1, 17 * S - 1], fill=inside)
    return img


OUTSIDE = (1, 1, 11, 17)
WINGS = [(24, 2, 34, 22), (36, 2, 46, 22)]


def rect(img, box, color):
    x0, y0, x1, y1 = box
    ImageDraw.Draw(img).rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], fill=color)


# charcoal with the orange bird big on the back
def mark():
    img = base(COAL, INK)
    vgrad(img, OUTSIDE, (30, 36, 46), (16, 19, 25))
    bird(img, 1 * S + 8, 1 * S + 22, 64, RUST)
    rect(img, (1, 16.5, 11, 17), RUST)
    for x0, y0, x1, y1 in WINGS:
        vgrad(img, (x0, y0, x1, y1), (30, 36, 46), (16, 19, 25))
        bird(img, x0 * S + 16, y0 * S + 40, 48, RUST)
        rect(img, (x0, y1 - 0.5, x1, y1), RUST)
    return img


# dark, a thin accent stripe down each side, a small bird low on the back
def rust():
    img = base(COAL, INK)
    vgrad(img, OUTSIDE, (24, 28, 36), (18, 21, 28))
    for box in [(2, 1, 2.5, 17), (9.5, 1, 10, 17)]:
        rect(img, box, RUST)
    for x0, y0, x1, y1 in WINGS:
        vgrad(img, (x0, y0, x1, y1), (24, 28, 36), (18, 21, 28))
        rect(img, (x0 + 1, y0, x0 + 1.5, y1), RUST)
        rect(img, (x1 - 1.5, y0, x1 - 1, y1), RUST)
    bird(img, 1 * S + 24, 1 * S + 88, 32, RUST)
    return img


# a dusk sky, deep blue down to orange, the bird dark against it
def dusk():
    img = base((27, 36, 64), INK)
    sky = [(22, 30, 58), (58, 46, 84), (176, 84, 66), (232, 132, 64)]
    for box in [OUTSIDE] + WINGS:
        x0, y0, x1, y1 = [v * S for v in box]
        dr = ImageDraw.Draw(img)
        for yy in range(y0, y1):
            t = (yy - y0) / (y1 - y0 - 1) * (len(sky) - 1)
            a, b, f = sky[int(t)], sky[min(int(t) + 1, len(sky) - 1)], t - int(t)
            dr.line([(x0, yy), (x1 - 1, yy)], fill=tuple(round(a[j] + (b[j] - a[j]) * f) for j in range(3)))
    bird(img, 1 * S + 12, 1 * S + 30, 56, INK)
    return img


# rows of soft feather chevrons in greys, the bottom edge dipped in orange
def feather():
    img = base(COAL, INK)
    for box in [OUTSIDE] + WINGS:
        vgrad(img, box, (34, 39, 48), (22, 26, 33))
        x0, y0, x1, y1 = [v * S for v in box]
        dr = ImageDraw.Draw(img)
        w = x1 - x0
        row = 0
        for yy in range(y0 + 6, y1 - 14, 14):
            c = (44, 50, 61) if row % 2 == 0 else (38, 43, 53)
            mid = x0 + w / 2
            dr.line([(x0 + 4, yy), (mid, yy + 10), (x1 - 4, yy)], fill=c, width=3, joint="curve")
            row += 1
        dr.rectangle([x0, y1 - 12, x1 - 1, y1 - 1], fill=RUST)
        dr.rectangle([x0, y1 - 16, x1 - 1, y1 - 13], fill=(150, 78, 44))
    return img


# the K with a wing for its arm, big and orange on charcoal, an orange hem
def kwing():
    img = base(COAL, INK)
    vgrad(img, OUTSIDE, (30, 36, 46), (16, 19, 25))
    bird(img, 1 * S + 12, 1 * S + 26, 56, RUST, "tools/marks/kwing.svg")
    rect(img, (1, 16.5, 11, 17), RUST)
    # the elytra mirrors one wing onto the other, a lopsided mark comes out as a backwards pair, so just the hem there
    for x0, y0, x1, y1 in WINGS:
        vgrad(img, (x0, y0, x1, y1), (30, 36, 46), (16, 19, 25))
        rect(img, (x0, y1 - 0.5, x1, y1), RUST)
    return img


# the front on stoop, tall and diving down the length of the back, on a slate fade
def stoop():
    img = base(COAL, INK)
    for box in [OUTSIDE] + WINGS:
        vgrad(img, box, (38, 44, 56), (14, 17, 22))
    bird(img, 1 * S, 1 * S + 22, 80, RUST, "tools/marks/stoop.svg")
    for x0, y0, x1, y1 in WINGS:
        rect(img, (x0, y1 - 0.5, x1, y1), RUST)
    return img


# the side on dive cutting down across the back, night blue fading to charcoal
def dive():
    img = base(COAL, INK)
    for box in [OUTSIDE] + WINGS:
        vgrad(img, box, (24, 34, 58), (14, 17, 22))
    bird(img, 1 * S + 4, 1 * S + 50, 66, RUST, "tools/marks/dive.svg")
    for x0, y0, x1, y1 in WINGS:
        rect(img, (x0, y1 - 0.5, x1, y1), RUST)
    return img


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    for name, fn in [("mark", mark), ("rust", rust), ("dusk", dusk), ("feather", feather), ("kwing", kwing), ("stoop", stoop), ("dive", dive)]:
        img = fn()
        # windows hands back "invalid argument" now and then while an editor or indexer has the old png open
        for tries in range(10):
            try:
                img.save(OUT + name + ".png")
                break
            except OSError:
                time.sleep(0.3)
        print("  -> " + name)
