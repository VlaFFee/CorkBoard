package com.corkboard.client;

import com.corkboard.BoardEntity;
import com.corkboard.CorkboardMod;
import com.corkboard.NoteType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рисует доску: пробковые клетки, деревянная рамка и записки.
 * Система координат: x вправо (для зрителя), y вверх, начало — центр доски.
 * Лицевая сторона сущности смотрит в -Z (как у картин).
 */
public class BoardRenderer extends EntityRenderer<BoardEntity> {
    private static final ResourceLocation CORK = new ResourceLocation(CorkboardMod.MODID, "textures/entity/cork.png");
    private static final ResourceLocation FRAME = new ResourceLocation(CorkboardMod.MODID, "textures/entity/frame.png");

    private static final float FRONT = -0.03f;
    private static final float FRAME_W = 0.1f;

    public BoardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(BoardEntity e) {
        return CORK;
    }

    @Override
    public void render(BoardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - e.getFacing().toYRot()));

        int w = e.blocksW(), h = e.blocksH();
        float hw = w / 2f, hh = h / 2f;

        // пробка
        VertexConsumer cork = buf.getBuffer(RenderType.entityCutoutNoCull(CORK));
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                float x0 = -hw + cx, y0 = hh - cy - 1;
                quad(cork, ps, x0, y0, x0 + 1, y0 + 1, FRONT, 0, 0, 1, 1, light);
            }
        }

        // рамка
        VertexConsumer fr = buf.getBuffer(RenderType.entityCutoutNoCull(FRAME));
        float z = FRONT - 0.004f;
        quad(fr, ps, -hw, hh - FRAME_W, hw, hh, z, 0, 0, 1, 1, light);
        quad(fr, ps, -hw, -hh, hw, -hh + FRAME_W, z, 0, 0, 1, 1, light);
        quad(fr, ps, -hw, -hh + FRAME_W, -hw + FRAME_W, hh - FRAME_W, z, 0, 0, 1, 1, light);
        quad(fr, ps, hw - FRAME_W, -hh + FRAME_W, hw, hh - FRAME_W, z, 0, 0, 1, 1, light);

        // записки
        List<BoardEntity.Note> notes = e.getNotes();
        Font font = this.getFont();
        for (int i = 0; i < notes.size(); i++) {
            BoardEntity.Note n = notes.get(i);
            NoteType t = n.type;
            float nz = FRONT - 0.008f - i * 0.0015f;
            float cx = n.x - hw, cy = hh - n.y;

            ps.pushPose();
            ps.translate(-cx, cy, 0); // локальный x зеркален относительно зрителя
            ps.mulPose(Axis.ZP.rotationDegrees(-n.rot));

            VertexConsumer nb = buf.getBuffer(RenderType.entityCutoutNoCull(t.texture));
            quad(nb, ps, -t.width() / 2, -t.height() / 2, t.width() / 2, t.height() / 2, nz, 0, 0, 1, 1, light);

            drawText(font, n, t, ps, buf, nz - 0.0008f, light);
            ps.popPose();
        }

        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private void drawText(Font font, BoardEntity.Note n, NoteType t, PoseStack ps, MultiBufferSource buf, float z, int light) {
        if (n.text == null || n.text.isEmpty()) return;
        float px = 1f / NoteType.PX_PER_BLOCK;
        int wrap = Math.max(8, Math.round(t.tw * px / NoteType.FONT_PX));
        int maxLines = Math.max(1, Math.round(t.th * px / NoteType.FONT_PX / 9f));

        ps.pushPose();
        // левый верхний угол области текста (в координатах зрителя, центр записки = 0)
        float left = -t.width() / 2 + t.tx * px;
        float top = t.height() / 2 - t.ty * px;
        ps.translate(-left, top, z);
        ps.scale(-NoteType.FONT_PX, -NoteType.FONT_PX, NoteType.FONT_PX);

        List<FormattedCharSequence> lines = font.split(Component.literal(n.text), wrap);
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            font.drawInBatch(lines.get(i), 0, i * 9f, t.textColor, false, m, buf,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        ps.popPose();
    }

    /** Четырёхугольник в координатах зрителя (x вправо, y вверх); u растёт вправо, v — вниз. */
    private static void quad(VertexConsumer vc, PoseStack ps, float x0, float y0, float x1, float y1,
                             float z, float u0, float v0, float u1, float v1, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, -x0, y1, z, u0, v0, light);
        v(vc, m, n, -x1, y1, z, u1, v0, light);
        v(vc, m, n, -x1, y0, z, u1, v1, light);
        v(vc, m, n, -x0, y0, z, u0, v1, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, -1).endVertex();
    }
}
