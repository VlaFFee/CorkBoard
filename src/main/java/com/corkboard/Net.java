package com.corkboard;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CorkboardMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, BoardAction.class, BoardAction::encode, BoardAction::decode, BoardAction::handle);
    }

    public static final int ADD = 0, UPDATE = 1, DELETE = 2, TEXT = 3;

    /** Клиент -> сервер: действие над запиской в окне доски (только креатив). */
    public record BoardAction(int entityId, int action, int index, float x, float y, float rot, String s) {
        public static BoardAction add(int id, NoteType t, float x, float y) { return new BoardAction(id, ADD, -1, x, y, 0, t.id); }
        public static BoardAction update(int id, int i, float x, float y, float rot) { return new BoardAction(id, UPDATE, i, x, y, rot, ""); }
        public static BoardAction delete(int id, int i) { return new BoardAction(id, DELETE, i, 0, 0, 0, ""); }
        public static BoardAction text(int id, int i, String s) { return new BoardAction(id, TEXT, i, 0, 0, 0, s); }

        static void encode(BoardAction m, FriendlyByteBuf b) {
            b.writeVarInt(m.entityId);
            b.writeByte(m.action);
            b.writeVarInt(m.index + 1);
            b.writeFloat(m.x);
            b.writeFloat(m.y);
            b.writeFloat(m.rot);
            b.writeUtf(m.s, 200);
        }

        static BoardAction decode(FriendlyByteBuf b) {
            return new BoardAction(b.readVarInt(), b.readByte(), b.readVarInt() - 1,
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readUtf(200));
        }

        static void handle(BoardAction m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p == null || !p.getAbilities().instabuild) return;
                Entity e = p.level().getEntity(m.entityId);
                if (!(e instanceof BoardEntity b) || b.distanceToSqr(p) > 64 * 64) return;
                if (!Float.isFinite(m.x) || !Float.isFinite(m.y) || !Float.isFinite(m.rot)) return;
                switch (m.action) {
                    case ADD -> b.addNote(NoteType.byId(m.s), m.x, m.y);
                    case UPDATE -> b.updateNote(m.index, m.x, m.y, m.rot);
                    case DELETE -> b.removeNote(m.index);
                    case TEXT -> b.setNoteText(m.index, m.s);
                    default -> { }
                }
            });
            c.setPacketHandled(true);
        }
    }
}
