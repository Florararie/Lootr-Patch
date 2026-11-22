package eu.pb4.lootrpatch.impl;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The gold motes Lootr drifts over a container a player has not looted yet.
 *
 * <p>Lootr spawns these on the client, out of its own {@code lootr:unopened_particle} type: a
 * {@code minecraft:glitter_0} quad, a twelfth of a block across, tinted {@code 0xFAD64A}, drifting
 * up by up to a fiftieth of a block and fading over thirty ticks, with one attempt in three per
 * client tick. A vanilla client has no such particle type, and particles are the one part of a
 * container a display entity cannot stand in for, so they are sent from here instead.
 *
 * <p>The nearest vanilla particle to it is dust, the only one that carries an arbitrary colour, and
 * it is given Lootr's own colour and about Lootr's size. What cannot come across is the sprite:
 * a vanilla client can only draw the dust sprite, not Lootr's glitter, without a resource pack.
 *
 * <p>Like the gold and silver looks, the motes are per player: Lootr asks each client whether
 * <em>that</em> player has opened the container, so a player who has looted it stops seeing them
 * while everyone else keeps them. A particle packet can be addressed to one player, which is what
 * {@link ServerLevel#sendParticles(ServerPlayer, net.minecraft.core.particles.ParticleOptions,
 * boolean, boolean, double, double, double, int, double, double, double, double)} does.
 */
final class UnopenedParticles {
    /** Lootr's {@code LootrAPI.DEFAULT_PARTICLE_COLOR}, at close to its {@code 0.12} quad size. */
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xfad64a, 0.8f);

    /** One attempt in three, the rate Lootr's own client uses. */
    private static final int ONE_IN = 3;

    /** How far up a mote may start, matching Lootr's {@code random * 0.02}. */
    private static final double RISE = 0.02;

    /** A mote's drift, which Lootr gives as up to {@code 0.02} upward and none sideways. */
    private static final double DRIFT = 0.01;

    private UnopenedParticles() {
    }

    /** Drifts one mote over {@code centre} for this player only, if this tick rolls one. */
    static void sparkle(ServerLevel world, ServerPlayer player, Vec3 centre, double spread) {
        sparkle(world, player, centre, spread, spread);
    }

    /**
     * The same, spreading the mote over a rectangle instead of a square: a frame's motes stay in the
     * plane of the frame, so they are spread across its face and not through the wall behind it.
     */
    static void sparkle(ServerLevel world, ServerPlayer player, Vec3 centre,
                        double spreadX, double spreadZ) {
        RandomSource random = world.getRandom();
        if (random.nextInt(ONE_IN) != 0) {
            return;
        }

        world.sendParticles(player, GOLD, true, false,
                centre.x + bounded(random, spreadX),
                centre.y + random.nextDouble() * RISE,
                centre.z + bounded(random, spreadZ),
                1, 0, 0, 0, DRIFT);
    }

    /** Anywhere across the given half-width, the way Lootr picks between its own bounds. */
    private static double bounded(RandomSource random, double halfWidth) {
        return (random.nextDouble() * 2 - 1) * halfWidth;
    }
}
