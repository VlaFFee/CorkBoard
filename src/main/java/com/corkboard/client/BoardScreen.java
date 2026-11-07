package com.corkboard.client;

import com.corkboard.BoardEntity;
import com.corkboard.Net;
import com.corkboard.NoteType;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Окно доски: вид спереди, как на референсе. В креативе внизу палитра записок,
 * записки можно ставить, таскать, поворачивать колесом, править текст и удалять.
 */
public class BoardScreen extends Screen {
    private static final ResourceLocation CORK = new ResourceLocation("corkboard", "textures/entity/cork.png");
    private static final ResourceLocation FRAME = new ResourceLocation("corkboard", "textures/entity/frame.png");
    private static final float FRAME_W = 0.1f;
    private static final int PAL = 34, PAL_GAP = 4;

    private final int entityId;
    private final boolean creative;

    private int unit, ox, oy;
    private int sel = -1;                 // выбранная записка
    private NoteType placing = null;      // тип записки «в руке»
    private boolean dragging = false;
    private float dragOffX, dragOffY, dragX, dragY, dragRot;
    private EditBox textBox;
    private Button deleteBtn;
    private boolean suppress = false;

    public BoardScreen(int entityId) {
        super(Component.empty());
        this.entityId = entityId;
        this.creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild;
    }

    private BoardEntity board() {
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId);
        return e instanceof BoardEntity b && !b.isRemoved() ? b : null;
    }

    private int reserveBottom() { return creative ? 84 : 24; }

    @Override
    protected void init() {
        BoardEntity b = board();
        if (b == null) { onClose(); return; }
        unit = Math.max(24, Math.min((this.width - 40) / b.blocksW(), (this.height - 12 - reserveBottom()) / b.blocksH()));
        ox = (this.width - unit * b.blocksW()) / 2;
        oy = 8;

        if (creative) {
            textBox = new EditBox(this.font, this.width / 2 - 150, this.height - 76, 240, 18, Component.translatable("gui.corkboard.text"));
            textBox.setMaxLength(200);
            textBox.setResponder(s -> {
                BoardEntity bb = board();
                if (!suppress && sel >= 0 && bb != null && sel < bb.getNotes().size() && !s.equals(bb.getNotes().get(sel).text)) {
                    Net.CHANNEL.sendToServer(Net.BoardAction.text(entityId, sel, s));
                }
            });
            textBox.setVisible(false);
            addRenderableWidget(textBox);
            deleteBtn = Button.builder(Component.translatable("gui.corkboard.delete"), bt -> deleteSelected())
                    .bounds(this.width / 2 + 96, this.height - 77, 54, 20).build();
            deleteBtn.visible = false;
            addRenderableWidget(deleteBtn);
        }
    }

    private void select(int idx) {
        sel = idx;
        BoardEntity b = board();
        boolean has = creative && idx >= 0 && b != null && idx < b.getNotes().size();
        if (textBox != null) {
            textBox.setVisible(has);
            if (has) {
                suppress = true;
                textBox.setValue(b.getNotes().get(idx).text);
                suppress = false;
            }
            if (!has) textBox.setFocused(false);
        }
        if (deleteBtn != null) deleteBtn.visible = has;
    }

    private void deleteSelected() {
        if (sel >= 0) Net.CHANNEL.sendToServer(Net.BoardAction.delete(entityId, sel));
        select(-1);
    }

    @Override
    public void tick() {
        BoardEntity b = board();
        if (b == null) { onClose(); return; }
        // новая записка приходит с сервера чуть позже — показать поле текста, когда она появится
        if (creative && textBox != null && sel >= 0 && sel < b.getNotes().size() && !textBox.visible) select(sel);
        if (sel >= b.getNotes().size() && !dragging && textBox != null && textBox.visible) select(-1);
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        BoardEntity b = board();
        if (b == null) return;
        this.renderBackground(g);
        int w = b.blocksW(), h = b.blocksH();
        int bw = unit * w, bh = unit * h;

        // пробка
        for (int cx = 0; cx < w; cx++)
            for (int cy = 0; cy < h; cy++)
                g.blit(CORK, ox + cx * unit, oy + cy * unit, unit, unit, 0, 0, 16, 16, 16, 16);

        // записки
        List<BoardEntity.Note> notes = b.getNotes();
        for (int i = 0; i < notes.size(); i++) {
            BoardEntity.Note n = notes.get(i);
            float x = n.x, y = n.y, rot = n.rot;
            if (dragging && i == sel) { x = dragX; y = dragY; rot = dragRot; }
            drawNote(g, n.type, n.text, x, y, rot, 1f, i == sel);
        }

        // рамка поверх
        int t = Math.round(unit * FRAME_W);
        g.blit(FRAME, ox, oy, bw, t, 0, 0, 16, 16, 16, 16);
        g.blit(FRAME, ox, oy + bh - t, bw, t, 0, 0, 16, 16, 16, 16);
        g.blit(FRAME, ox, oy + t, t, bh - 2 * t, 0, 0, 16, 16, 16, 16);
        g.blit(FRAME, ox + bw - t, oy + t, t, bh - 2 * t, 0, 0, 16, 16, 16, 16);
        g.renderOutline(ox - 1, oy - 1, bw + 2, bh + 2, 0xFF1A1208);

        // «призрак» записки в руке
        if (placing != null && inBoard(mx, my)) {
            drawNote(g, placing, "", (mx - ox) / (float) unit, (my - oy) / (float) unit, 0, 0.7f, false);
        }

        if (creative) renderPalette(g, mx, my);
        else g.drawCenteredString(this.font, Component.translatable("gui.corkboard.hint_view"), this.width / 2, this.height - 16, 0xA0A0A0);

        super.render(g, mx, my, pt);

        if (creative) {
            int hover = paletteAt(mx, my);
            if (hover >= 0) g.renderTooltip(this.font, Component.translatable("note.corkboard." + NoteType.values()[hover].id), mx, my);
        }
    }

    private void drawNote(GuiGraphics g, NoteType t, String text, float bx, float by, float rot, float alpha, boolean selected) {
        float s = unit / (float) NoteType.PX_PER_BLOCK;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ox + bx * unit, oy + by * unit, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-rot));
        pose.scale(s, s, 1);

        if (alpha < 1f) g.setColor(1, 1, 1, alpha);
        g.blit(t.texture, -t.texW / 2, -t.texH / 2, t.texW, t.texH, 0, 0, t.texW, t.texH, t.texW, t.texH);
        if (alpha < 1f) g.setColor(1, 1, 1, 1);
        if (selected) g.renderOutline(-t.texW / 2 - 1, -t.texH / 2 - 1, t.texW + 2, t.texH + 2, 0xFFFFFFFF);

        if (text != null && !text.isEmpty()) {
            pose.pushPose();
            pose.translate(-t.texW / 2f + t.tx, -t.texH / 2f + t.ty, 0);
            float fs = NoteType.FONT_PX * NoteType.PX_PER_BLOCK; // 0.5
            pose.scale(fs, fs, 1);
            int wrap = Math.round(t.tw / fs);
            int maxLines = Math.max(1, Math.round(t.th / fs / 9f));
            List<FormattedCharSequence> lines = this.font.split(Component.literal(text), wrap);
            for (int i = 0; i < lines.size() && i < maxLines; i++) {
                g.drawString(this.font, lines.get(i), 0, i * 9, t.textColor, false);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    private void renderPalette(GuiGraphics g, int mx, int my) {
        NoteType[] types = NoteType.values();
        int total = types.length * (PAL + PAL_GAP) - PAL_GAP;
        int x0 = (this.width - total) / 2, y0 = this.height - 48;
        for (int i = 0; i < types.length; i++) {
            int x = x0 + i * (PAL + PAL_GAP);
            boolean on = types[i] == placing;
            g.fill(x - 1, y0 - 1, x + PAL + 1, y0 + PAL + 1, on ? 0xFFFFFFFF : 0xFF000000);
            g.fill(x, y0, x + PAL, y0 + PAL, 0xFF8A5A30);
            NoteType t = types[i];
            float sc = Math.min(28f / t.texW, 28f / t.texH);
            int iw = Math.max(1, Math.round(t.texW * sc)), ih = Math.max(1, Math.round(t.texH * sc));
            g.blit(t.texture, x + (PAL - iw) / 2, y0 + (PAL - ih) / 2, iw, ih, 0, 0, t.texW, t.texH, t.texW, t.texH);
        }
        g.drawCenteredString(this.font, Component.translatable("gui.corkboard.hint"), this.width / 2, this.height - 10, 0xA0A0A0);
    }

    private int paletteAt(int mx, int my) {
        NoteType[] types = NoteType.values();
        int total = types.length * (PAL + PAL_GAP) - PAL_GAP;
        int x0 = (this.width - total) / 2, y0 = this.height - 48;
        if (my < y0 || my >= y0 + PAL) return -1;
        for (int i = 0; i < types.length; i++) {
            int x = x0 + i * (PAL + PAL_GAP);
            if (mx >= x && mx < x + PAL) return i;
        }
        return -1;
    }

    private boolean inBoard(double mx, double my) {
        BoardEntity b = board();
        return b != null && mx >= ox && my >= oy && mx < ox + unit * b.blocksW() && my < oy + unit * b.blocksH();
    }

    // -------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        BoardEntity b = board();
        if (b == null || !creative) return false;

        if (button == 1) { placing = null; return true; }
        if (button != 0) return false;

        int pal = paletteAt((int) mx, (int) my);
        if (pal >= 0) {
            NoteType t = NoteType.values()[pal];
            placing = placing == t ? null : t;
            select(-1);
            return true;
        }
        if (!inBoard(mx, my)) return false;

        float bx = (float) ((mx - ox) / unit), by = (float) ((my - oy) / unit);
        if (placing != null) {
            Net.CHANNEL.sendToServer(Net.BoardAction.add(entityId, placing, bx, by));
            // новая записка окажется последней
            select(b.getNotes().size());
            return true;
        }
        int idx = b.noteAt(bx, by);
        select(idx);
        if (idx >= 0) {
            BoardEntity.Note n = b.getNotes().get(idx);
            dragging = true;
            dragOffX = bx - n.x;
            dragOffY = by - n.y;
            dragX = n.x;
            dragY = n.y;
            dragRot = n.rot;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            dragX = (float) ((mx - ox) / unit) - dragOffX;
            dragY = (float) ((my - oy) / unit) - dragOffY;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            BoardEntity b = board();
            if (b != null && sel >= 0 && sel < b.getNotes().size()) {
                Net.CHANNEL.sendToServer(Net.BoardAction.update(entityId, sel, dragX, dragY, dragRot));
                sel = b.getNotes().size() - 1; // сервер поднимет её наверх
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        BoardEntity b = board();
        if (creative && sel >= 0 && b != null && sel < b.getNotes().size()) {
            BoardEntity.Note n = b.getNotes().get(sel);
            float rot = n.rot + (float) delta * 5f;
            Net.CHANNEL.sendToServer(Net.BoardAction.update(entityId, sel, n.x, n.y, rot));
            sel = b.getNotes().size() - 1;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && placing != null) { placing = null; return true; }
        if (key == 261 && creative && sel >= 0 && !(textBox != null && textBox.isFocused())) { // Delete
            deleteSelected();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
