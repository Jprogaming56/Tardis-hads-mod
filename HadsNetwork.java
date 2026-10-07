package com.personal.hadsswitch;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Tells players' games when to start the HADS music and when to fade it out. The fade is done on the player's
 * side because the server cannot change the volume of a sound that is already playing.
 */
public final class HadsNetwork {
    private static final String PROTOCOL = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(HadsSwitchMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private HadsNetwork() {}

    /**
     * start = true begins the music, false fades it out. inside = the player is in the TARDIS interior, so the
     * music is not tied to a spot in the world. x/y/z is where the exterior is parked.
     */
    public record Music(boolean start, boolean inside, double x, double y, double z) {
        public static Music begin(boolean inside, double x, double y, double z) {
            return new Music(true, inside, x, y, z);
        }

        public static Music fade() {
            return new Music(false, false, 0, 0, 0);
        }
    }

    public static void register() {
        CHANNEL.registerMessage(0, Music.class, HadsNetwork::encode, HadsNetwork::decode, HadsNetwork::handle);
    }

    public static void send(ServerPlayer player, Music message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    private static void encode(Music m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.start());
        buf.writeBoolean(m.inside());
        buf.writeDouble(m.x());
        buf.writeDouble(m.y());
        buf.writeDouble(m.z());
    }

    private static Music decode(FriendlyByteBuf buf) {
        return new Music(buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void handle(Music m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HadsClient.handle(m)));
        ctx.get().setPacketHandled(true);
    }
}
