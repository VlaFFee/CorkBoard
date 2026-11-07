package com.corkboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Висячая сущность: пробковая доска размером 1..5 блоков по каждой стороне. */
public class BoardEntity extends HangingEntity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<Integer> W = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> H = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> NOTES = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public static final int MAX_NOTES = 64;

    public BoardEntity(EntityType<? extends BoardEntity> type, Level level) {
        super(type, level);
    }

    public BoardEntity(Level level, BlockPos pos, Direction dir, int w, int h) {
        super(CorkboardMod.BOARD.get(), level, pos);
        this.entityData.set(W, w);
        this.entityData.set(H, h);
        this.setDirection(dir);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(W, 2);
        this.entityData.define(H, 2);
        this.entityData.define(NOTES, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == W || key == H) this.recalculateBoundingBox();
    }

    /** Размер в блоках. */
    public int blocksW() { return this.entityData.get(W); }
    public int blocksH() { return this.entityData.get(H); }

    // HangingEntity работает в «пикселях» (16 на блок)
    @Override public int getWidth() { return blocksW() * 16; }
    @Override public int getHeight() { return blocksH() * 16; }

    public Direction getFacing() { return this.direction; }

    // ---------------------------------------------------------------- notes

    public static class Note {
        public NoteType type;
        public float x, y;   // центр записки в блоках от левого верхнего угла доски
        public float rot;    // градусы
        public String text;

        public Note(NoteType type, float x, float y, float rot, String text) {
            this.type = type; this.x = x; this.y = y; this.rot = rot; this.text = text;
        }
    }

    public List<Note> getNotes() {
        List<Note> out = new ArrayList<>();
        ListTag list = this.entityData.get(NOTES).getList("n", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            out.add(new Note(NoteType.byId(t.getString("t")), t.getFloat("x"), t.getFloat("y"), t.getFloat("r"), t.getString("s")));
        }
        return out;
    }

    private void setNotes(List<Note> notes) {
        ListTag list = new ListTag();
        for (Note n : notes) {
            CompoundTag t = new CompoundTag();
            t.putString("t", n.type.id);
            t.putFloat("x", n.x);
            t.putFloat("y", n.y);
            t.putFloat("r", n.rot);
            t.putString("s", n.text);
            list.add(t);
        }
        CompoundTag root = new CompoundTag();
        root.put("n", list);
        this.entityData.set(NOTES, root);
    }

    /** Индекс самой верхней записки под точкой или -1. */
    public int noteAt(double bx, double by) {
        List<Note> notes = getNotes();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            double dx = bx - n.x, dy = by - n.y;
            double a = Math.toRadians(n.rot);
            double lx = dx * Math.cos(a) - dy * Math.sin(a);
            double ly = dx * Math.sin(a) + dy * Math.cos(a);
            if (Math.abs(lx) <= n.type.width() / 2 && Math.abs(ly) <= n.type.height() / 2) return i;
        }
        return -1;
    }

    public void addNote(NoteType type, double bx, double by) {
        List<Note> notes = getNotes();
        if (notes.size() >= MAX_NOTES) return;
        float hw = type.width() / 2, hh = type.height() / 2;
        float x = (float) Math.max(hw * 0.6, Math.min(blocksW() - hw * 0.6, bx));
        float y = (float) Math.max(hh * 0.6, Math.min(blocksH() - hh * 0.6, by));
        float rot = (new Random().nextFloat() - 0.5f) * 16f;
        notes.add(new Note(type, x, y, rot, type.defaultText));
        setNotes(notes);
    }

    /** Двигает/поворачивает записку и поднимает её поверх остальных. */
    public void updateNote(int index, double bx, double by, float rot) {
        List<Note> notes = getNotes();
        if (index < 0 || index >= notes.size()) return;
        Note n = notes.remove(index);
        float hw = n.type.width() / 2, hh = n.type.height() / 2;
        n.x = (float) Math.max(hw * 0.6, Math.min(blocksW() - hw * 0.6, bx));
        n.y = (float) Math.max(hh * 0.6, Math.min(blocksH() - hh * 0.6, by));
        n.rot = Math.max(-180f, Math.min(180f, rot));
        notes.add(n);
        setNotes(notes);
    }

    public void removeNote(int index) {
        List<Note> notes = getNotes();
        if (index < 0 || index >= notes.size()) return;
        notes.remove(index);
        setNotes(notes);
    }

    public void setNoteText(int index, String text) {
        List<Note> notes = getNotes();
        if (index < 0 || index >= notes.size()) return;
        notes.get(index).text = text.length() > 200 ? text.substring(0, 200) : text;
        setNotes(notes);
    }

    // ---------------------------------------------------------- interaction

    @Override
    public InteractionResult interactAt(Player player, Vec3 rel, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) com.corkboard.client.ClientHooks.openBoard(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void dropItem(Entity breaker) {
        if (!level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) return;
        playSound(SoundEvents.PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof Player p && p.getAbilities().instabuild) return;
        spawnAtLocation(BoardItem.withSize(blocksW(), blocksH()));
    }

    @Override
    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0f, 1.0f);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        setPos(x, y, z);
    }

    @Override
    public ItemStack getPickedResult(net.minecraft.world.phys.HitResult target) {
        return BoardItem.withSize(blocksW(), blocksH());
    }

    // ------------------------------------------------------------ save/sync

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Facing", (byte) this.direction.get2DDataValue());
        tag.putInt("W", blocksW());
        tag.putInt("H", blocksH());
        tag.put("Notes", this.entityData.get(NOTES).getList("n", Tag.TAG_COMPOUND).copy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(W, Math.max(1, Math.min(5, tag.getInt("W"))));
        this.entityData.set(H, Math.max(1, Math.min(5, tag.getInt("H"))));
        CompoundTag root = new CompoundTag();
        root.put("n", tag.getList("Notes", Tag.TAG_COMPOUND));
        this.entityData.set(NOTES, root);
        super.readAdditionalSaveData(tag);
        this.setDirection(Direction.from2DDataValue(tag.getByte("Facing")));
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.direction.get2DDataValue());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.setDirection(Direction.from2DDataValue(buf.readByte()));
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.corkboard.board");
    }
}
