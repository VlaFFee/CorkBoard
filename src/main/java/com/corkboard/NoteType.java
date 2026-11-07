package com.corkboard;

import net.minecraft.resources.ResourceLocation;

/**
 * Виды записок. Размеры текстур в пикселях, PX_PER_BLOCK пикселей = 1 блок доски.
 * Область текста (tx, ty, tw, th) задана в пикселях текстуры.
 */
public enum NoteType {
    TORN_STRIP("torn_strip", 76, 24, 5, 6, 66, 14, 0xFF3A3A44, "Объявление"),
    NEWSPAPER("newspaper", 40, 70, 3, 3, 34, 9, 0xFF2A2A2A, "Вечерняя пресса"),
    STICKY_PINK("sticky_pink", 28, 38, 3, 6, 22, 28, 0xFF4A3A44, "Не забыть!"),
    STICKY_GREEN("sticky_green", 48, 34, 4, 6, 40, 24, 0xFF3C5A3C, "Список дел"),
    STICKY_YELLOW("sticky_yellow", 34, 32, 3, 6, 28, 22, 0xFF5A4A2A, "Заметка"),
    PAPER_LINED("paper_lined", 36, 28, 3, 6, 30, 19, 0xFF2E3C6E, "Кто знал?"),
    NOTE_WHITE("note_white", 22, 28, 2, 5, 18, 19, 0xFF3A3A3A, "Текст"),
    SKETCH_GRID("sketch_grid", 26, 36, 2, 2, 22, 32, 0xFF3A3A3A, "");

    public static final int PX_PER_BLOCK = 64;
    /** Размер одного пикселя шрифта в блоках. */
    public static final float FONT_PX = 0.5f / PX_PER_BLOCK;

    public final String id;
    public final int texW, texH;
    public final int tx, ty, tw, th;
    public final int textColor;
    public final String defaultText;
    public final ResourceLocation texture;

    NoteType(String id, int texW, int texH, int tx, int ty, int tw, int th, int textColor, String defaultText) {
        this.id = id;
        this.texW = texW;
        this.texH = texH;
        this.tx = tx;
        this.ty = ty;
        this.tw = tw;
        this.th = th;
        this.textColor = textColor;
        this.defaultText = defaultText;
        this.texture = new ResourceLocation(CorkboardMod.MODID, "textures/entity/note_" + id + ".png");
    }

    public float width() { return texW / (float) PX_PER_BLOCK; }
    public float height() { return texH / (float) PX_PER_BLOCK; }

    public static NoteType byId(String id) {
        for (NoteType t : values()) if (t.id.equals(id)) return t;
        return STICKY_YELLOW;
    }
}
