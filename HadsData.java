package com.personal.hadsswitch;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers which TARDISes have HADS switched on (and who switched it on), saved with the world. */
public class HadsData extends SavedData {
    private static final String NAME = "hadsswitch_enabled";
    private final Set<UUID> enabled = new HashSet<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    /** Normal arrival (MAT) animation to put back after a HADS trip. */
    private final Map<UUID, String> pendingRestore = new HashMap<>();
    /** Normal departure (DEMAT) animation to put back after a HADS trip. */
    private final Map<UUID, String> pendingDematRestore = new HashMap<>();

    public static HadsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HadsData::load, HadsData::new, NAME);
    }

    public static HadsData load(CompoundTag tag) {
        HadsData data = new HadsData();
        ListTag list = tag.getList("enabled", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.enabled.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        CompoundTag own = tag.getCompound("owners");
        for (String key : own.getAllKeys()) {
            try {
                data.owners.put(UUID.fromString(key), UUID.fromString(own.getString(key)));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        CompoundTag pend = tag.getCompound("pending_restore");
        for (String key : pend.getAllKeys()) {
            try {
                data.pendingRestore.put(UUID.fromString(key), pend.getString(key));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        CompoundTag pendDemat = tag.getCompound("pending_restore_demat");
        for (String key : pendDemat.getAllKeys()) {
            try {
                data.pendingDematRestore.put(UUID.fromString(key), pendDemat.getString(key));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : enabled) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("enabled", list);
        CompoundTag own = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : owners.entrySet()) {
            own.putString(e.getKey().toString(), e.getValue().toString());
        }
        tag.put("owners", own);
        CompoundTag pend = new CompoundTag();
        for (Map.Entry<UUID, String> e : pendingRestore.entrySet()) {
            pend.putString(e.getKey().toString(), e.getValue());
        }
        tag.put("pending_restore", pend);
        CompoundTag pendDemat = new CompoundTag();
        for (Map.Entry<UUID, String> e : pendingDematRestore.entrySet()) {
            pendDemat.putString(e.getKey().toString(), e.getValue());
        }
        tag.put("pending_restore_demat", pendDemat);
        return tag;
    }

    public boolean isEnabled(UUID id) {
        return enabled.contains(id);
    }

    public void setEnabled(UUID id, boolean value) {
        boolean changed = value ? enabled.add(id) : enabled.remove(id);
        if (changed) {
            setDirty();
        }
    }

    public Set<UUID> enabledIds() {
        return Collections.unmodifiableSet(enabled);
    }

    public UUID getOwner(UUID tardisId) {
        return owners.get(tardisId);
    }

    public void setOwner(UUID tardisId, UUID playerId) {
        UUID old = owners.put(tardisId, playerId);
        if (!playerId.equals(old)) {
            setDirty();
        }
    }

    private Map<UUID, String> restoreMap(String state) {
        return "DEMAT".equals(state) ? pendingDematRestore : pendingRestore;
    }

    /** The normal animation (for "MAT" or "DEMAT") to put back after a HADS trip, or null if nothing is pending. */
    public String getPendingRestore(UUID tardisId, String state) {
        return restoreMap(state).get(tardisId);
    }

    public void setPendingRestore(UUID tardisId, String state, String animationId) {
        restoreMap(state).put(tardisId, animationId);
        setDirty();
    }

    public void clearPendingRestore(UUID tardisId, String state) {
        if (restoreMap(state).remove(tardisId) != null) {
            setDirty();
        }
    }

    public boolean hasPendingRestore(UUID tardisId) {
        return pendingRestore.containsKey(tardisId) || pendingDematRestore.containsKey(tardisId);
    }

    public Set<UUID> pendingRestoreIds() {
        Set<UUID> all = new HashSet<>(pendingRestore.keySet());
        all.addAll(pendingDematRestore.keySet());
        return Collections.unmodifiableSet(all);
    }
}
