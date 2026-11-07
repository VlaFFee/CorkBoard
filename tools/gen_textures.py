#!/usr/bin/env python3
"""Генератор пиксель-арт текстур мода. Запуск: python3 tools/gen_textures.py"""
import random, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/corkboard/textures")
random.seed(7)

def save(img, sub, name):
    os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    img.save(os.path.join(OUT, sub, name))

# ---------- пробка (тайл 16x16, цвета как на скрине: оранжевая) ----------
base = [(0xC8,0x6A,0x32),(0xD0,0x72,0x38),(0xBE,0x62,0x2C),(0xC0,0x66,0x30)]
cork = Image.new("RGBA",(16,16))
for y in range(16):
    for x in range(16):
        c = random.choice(base)
        cork.putpixel((x,y), c+(255,))
for _ in range(14):
    x,y = random.randrange(16), random.randrange(16)
    cork.putpixel((x,y),(0x9C,0x4C,0x20,255))
for _ in range(8):
    x,y = random.randrange(16), random.randrange(16)
    cork.putpixel((x,y),(0xE0,0x8A,0x4C,255))
save(cork,"entity","cork.png")

# ---------- рамка (дерево) ----------
fr = Image.new("RGBA",(16,16))
d = ImageDraw.Draw(fr)
for y in range(16):
    shade = (0xE4,0xA8,0x58) if y<3 else (0xD6,0x96,0x42) if y<13 else (0xB8,0x78,0x30)
    for x in range(16):
        j = random.choice([-6,0,0,6])
        fr.putpixel((x,y),tuple(max(0,min(255,c+j)) for c in shade)+(255,))
save(fr,"entity","frame.png")

# ---------- записки ----------
def torn(img, edges, seed):
    """Рваные края: выкусываем пиксели по заданным сторонам."""
    r = random.Random(seed)
    w,h = img.size
    px = img.load()
    for side in edges:
        if side in "tb":
            for x in range(w):
                depth = r.choice([0,1,1,2])
                for k in range(depth):
                    y = k if side=="t" else h-1-k
                    px[x,y]=(0,0,0,0)
        else:
            for y in range(h):
                depth = r.choice([0,1,1,2])
                for k in range(depth):
                    x = k if side=="l" else w-1-k
                    px[x,y]=(0,0,0,0)

def paper(w,h,col,edge=(0,0,0),speck=4):
    img = Image.new("RGBA",(w,h),col+(255,))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if random.random()<0.08:
                px[x,y]=tuple(max(0,c-speck*2) for c in col)+(255,)
    d = ImageDraw.Draw(img)
    # тонкая тень по низу/правому краю
    d.line([(0,h-1),(w-1,h-1)],fill=tuple(max(0,c-28) for c in col)+(255,))
    d.line([(w-1,0),(w-1,h-1)],fill=tuple(max(0,c-20) for c in col)+(255,))
    return img

def pin(img, cx, cy):
    px = img.load()
    for dx,dy,c in [(0,0,(0x9A,0xA0,0xA8)),(1,0,(0xC8,0xCE,0xD4)),(0,1,(0x70,0x76,0x80)),(1,1,(0x8A,0x90,0x98)),(2,1,(0x50,0x54,0x5C))]:
        x,y = cx+dx,cy+dy
        if 0<=x<img.size[0] and 0<=y<img.size[1]: px[x,y]=c+(255,)

def lines(img, y0, y1, step, x0, x1, col):
    d = ImageDraw.Draw(img)
    for y in range(y0,y1,step): d.line([(x0,y),(x1,y)],fill=col)

def fold(img, x, col):
    d = ImageDraw.Draw(img)
    d.line([(x,0),(x,img.size[1]-1)],fill=col)

mk = {}

# рваная белая полоса
im = paper(76,24,(0xEE,0xF2,0xEE)); torn(im,"tb",1); torn(im,"r",11); pin(im,37,1)
mk["torn_strip"]=im

# газета: заголовок, колонки текста (серые линии), блок со «сводкой», фото
im = paper(40,70,(0xE6,0xE0,0xCC),speck=3)
d = ImageDraw.Draw(im)
d.rectangle([3,2,36,12],outline=(0x30,0x30,0x30,255))
# портрет справа вверху (как на референсе)
d.rectangle([26,14,36,28],fill=(0x78,0x78,0x78,255))
d.rectangle([28,16,34,22],fill=(0xC8,0xA8,0x90,255)); d.rectangle([28,23,34,28],fill=(0x30,0x30,0x38,255))
lines(im,15,30,3,3,24,(0x30,0x30,0x30,255))
d.rectangle([3,31,17,35],fill=(0xB8,0x30,0x30,255))      # красная плашка
for i in range(5): d.rectangle([20+i*4,31,22+i*4,35],fill=(0x70,0x58,0x90,255))  # табло
d.rectangle([3,38,10,44],fill=(0x60,0xA0,0x50,255))
lines(im,38,46,2,12,36,(0x40,0x40,0x40,255))
d.rectangle([14,48,30,62],fill=(0x44,0x44,0x4C,255)); d.rectangle([19,51,25,59],fill=(0x7A,0x4A,0x2C,255))  # книга
lines(im,48,64,3,3,12,(0x30,0x30,0x30,255))
lines(im,64,69,2,3,36,(0x30,0x30,0x30,255))
# загнутый угол
for k in range(6):
    for j in range(k+1): im.putpixel((39-j,69-k+0) if False else (39-j,69-(5-k)),(0,0,0,0))
torn(im,"t",3); pin(im,19,0)
mk["newspaper"]=im

# розовый стикер, слегка загнутый угол
im = paper(28,38,(0xF2,0xA0,0xA8)); d = ImageDraw.Draw(im)
for k in range(6):
    for j in range(k+1): im.putpixel((27-j,37-(5-k)),(0,0,0,0))
d.polygon([(27,32),(21,37),(27,37)],fill=(0xD0,0x7C,0x88,255))
pin(im,13,1); torn(im,"l",5)
mk["sticky_pink"]=im

# зелёная записка
im = paper(48,34,(0xB8,0xD8,0x98)); torn(im,"r",9); torn(im,"b",2); pin(im,35,1)
mk["sticky_green"]=im

# жёлтая записка со сгибом (как оранжево-бежевая на референсе)
im = paper(34,32,(0xF2,0xCC,0x84)); fold(im,16,(0xD8,0xAA,0x5C,255)); fold(im,17,(0xFA,0xDC,0xA0,255))
torn(im,"t",4); pin(im,15,1)
mk["sticky_yellow"]=im

# листок в линейку (голубые линии, красное поле)
im = paper(36,28,(0xE8,0xEE,0xF8)); lines(im,5,27,4,0,35,(0xA8,0xBC,0xE0,255))
ImageDraw.Draw(im).line([(4,0),(4,27)],fill=(0xE0,0x80,0x80,255)); torn(im,"l",8); torn(im,"t",6); pin(im,28,1)
mk["paper_lined"]=im

# белая записка
im = paper(22,28,(0xF0,0xF0,0xF0)); torn(im,"b",12); pin(im,10,1)
mk["note_white"]=im

# клетчатый набросок 3x3
im = paper(26,36,(0xEC,0xE8,0xDC)); d = ImageDraw.Draw(im)
for i in range(4):
    p = 3+i*7
    d.line([(p,3),(p,24)],fill=(0x48,0x48,0x48,255)); d.line([(3,p),(24,p)],fill=(0x48,0x48,0x48,255))
lines(im,28,35,3,4,22,(0x60,0x60,0x60,255))
pin(im,12,0); torn(im,"t",13)
mk["sketch_grid"]=im

for k,v in mk.items():
    save(v,"entity",f"note_{k}.png")

# иконка доски 16x16: рамка + пробка + мини-записки
ic = Image.new("RGBA",(16,16),(0,0,0,0)); d = ImageDraw.Draw(ic)
d.rectangle([1,2,14,13],fill=(0xD6,0x96,0x42,255)); d.rectangle([2,3,13,12],fill=(0xC8,0x6A,0x32,255))
d.rectangle([3,4,6,7],fill=(0xF2,0xA0,0xA8,255)); d.rectangle([8,4,12,6],fill=(0xB8,0xD8,0x98,255))
d.rectangle([7,8,10,11],fill=(0xF2,0xCC,0x84,255)); d.rectangle([3,9,5,11],fill=(0xEE,0xF2,0xEE,255))
ic.putpixel((4,4),(0x80,0x88,0x90,255)); ic.putpixel((10,4),(0x80,0x88,0x90,255))
save(ic,"item","board.png")
print("ok")
