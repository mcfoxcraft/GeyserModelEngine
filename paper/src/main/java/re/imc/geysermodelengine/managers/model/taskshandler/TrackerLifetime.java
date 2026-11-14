package re.imc.geysermodelengine.managers.model.taskshandler;

import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * How long the Bedrock copy of a BetterModel tracker lives: as long as the tracker does.
 *
 * <p>BetterModel plays a blueprint's {@code death} animation on the trackers of a mob that died and marks them for
 * removal; the tracker outlives the mob and closes itself when the animation is over. The Bedrock copy stays
 * through that animation, so that Bedrock players see it too, and goes when the tracker is closed. A tracker that
 * plays no death animation is closed by BetterModel as its mob leaves the world (about a second after a death, at
 * once for a mob removed alive or an unloaded chunk), and its copy goes then.
 *
 * <p>From the moment the mob is dead (or its tracker marked for removal) the copy is "dying": it is shown to nobody
 * new, and it goes at the latest {@link #DYING_GRACE_NANOS} after the blueprint's death animation would have ended,
 * should BetterModel never close the tracker (a tracker that is drawn for nobody stops ticking, and a death that
 * starts while it is stopped never ends).
 *
 * <p>Not thread-safe: one task updates it.
 */
final class TrackerLifetime {

    /** What the copy does next. */
    enum Stage {
        /** The mob lives and so does its tracker: the copy is drawn as always. */
        LIVING,
        /** The mob is dead, or its tracker marked for removal, and the tracker is still open: playing its death. */
        DYING,
        /** The tracker is closed, or it has been dying for too long: the copy goes. */
        GONE
    }

    /**
     * Time a dying copy is given on top of the blueprint's death animation: the delay before the animation starts,
     * and the second Minecraft takes to remove a dead mob (a tracker without a death animation is closed then),
     * with room for a slow server.
     */
    static final long DYING_GRACE_NANOS = TimeUnit.SECONDS.toNanos(3);

    private boolean dying;
    private long deadline;

    /**
     * @param trackerClosed whether the tracker is closed
     * @param dying whether the mob is dead or the tracker marked for removal
     * @param deathSeconds the length of the blueprint's death animation in seconds, 0 without one; read once, when
     *                     the copy starts dying
     * @param now {@link System#nanoTime()}
     */
    Stage update(boolean trackerClosed, boolean dying, DoubleSupplier deathSeconds, long now) {
        if (trackerClosed) return Stage.GONE;

        if (!dying) {
            // A mob whose death was cancelled lives on; a later death gets a full window again.
            this.dying = false;
            return Stage.LIVING;
        }

        if (!this.dying) {
            this.dying = true;
            double seconds = Math.max(0, deathSeconds.getAsDouble());
            this.deadline = now + DYING_GRACE_NANOS + (long) (seconds * TimeUnit.SECONDS.toNanos(1));
        }

        return now - deadline >= 0 ? Stage.GONE : Stage.DYING;
    }
}
