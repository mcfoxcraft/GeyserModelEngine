package re.imc.geysermodelengine.managers.model.taskshandler;

import org.junit.jupiter.api.Test;
import re.imc.geysermodelengine.managers.model.taskshandler.TrackerLifetime.Stage;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerLifetimeTest {

    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long GRACE = TrackerLifetime.DYING_GRACE_NANOS;

    /** The Goblin Giant's death animation on the kingdoms test server: 7.2917 s. */
    private static final DoubleSupplier GIANT_DEATH = () -> 7.2917;
    private static final DoubleSupplier NO_DEATH = () -> 0;

    private final TrackerLifetime lifetime = new TrackerLifetime();

    @Test
    void aLivingMobWithAnOpenTrackerLives() {
        assertEquals(Stage.LIVING, lifetime.update(false, false, GIANT_DEATH, 0));
        assertEquals(Stage.LIVING, lifetime.update(false, false, GIANT_DEATH, 100 * SECOND));
    }

    @Test
    void aDeadMobKeepsItsCopyWhileTheTrackerPlaysItsDeath() {
        long death = 50 * SECOND;
        assertEquals(Stage.DYING, lifetime.update(false, true, GIANT_DEATH, death));
        assertEquals(Stage.DYING, lifetime.update(false, true, GIANT_DEATH, death + 7 * SECOND));
    }

    @Test
    void theCopyGoesWhenTheTrackerCloses() {
        assertEquals(Stage.DYING, lifetime.update(false, true, GIANT_DEATH, 0));
        assertEquals(Stage.GONE, lifetime.update(true, true, GIANT_DEATH, 7 * SECOND));
    }

    @Test
    void aTrackerClosedOnALivingMobTakesItsCopyAway() {
        assertEquals(Stage.LIVING, lifetime.update(false, false, GIANT_DEATH, 0));
        assertEquals(Stage.GONE, lifetime.update(true, false, GIANT_DEATH, SECOND));
    }

    @Test
    void aDyingCopyGoesOnItsOwnSomeTimeAfterTheDeathAnimationWouldHaveEnded() {
        long death = 10 * SECOND;
        long end = death + (long) (7.2917 * SECOND) + GRACE;
        assertEquals(Stage.DYING, lifetime.update(false, true, GIANT_DEATH, death));
        assertEquals(Stage.DYING, lifetime.update(false, true, GIANT_DEATH, end - 1));
        assertEquals(Stage.GONE, lifetime.update(false, true, GIANT_DEATH, end));
    }

    @Test
    void withoutADeathAnimationTheCopyWaitsOnlyTheGraceForTheTrackerToClose() {
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, 0));
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, GRACE - 1));
        assertEquals(Stage.GONE, lifetime.update(false, true, NO_DEATH, GRACE));
    }

    @Test
    void aNegativeLengthCountsAsNone() {
        assertEquals(Stage.DYING, lifetime.update(false, true, () -> -5, 0));
        assertEquals(Stage.GONE, lifetime.update(false, true, () -> -5, GRACE));
    }

    @Test
    void theDeathLengthIsReadOnceWhenTheCopyStartsDying() {
        AtomicInteger reads = new AtomicInteger();
        DoubleSupplier counted = () -> {
            reads.incrementAndGet();
            return 1;
        };
        lifetime.update(false, false, counted, 0);
        assertEquals(0, reads.get());
        lifetime.update(false, true, counted, SECOND);
        lifetime.update(false, true, counted, 2 * SECOND);
        lifetime.update(false, true, counted, 3 * SECOND);
        assertEquals(1, reads.get());
    }

    @Test
    void aMobThatDidNotDieAfterAllLivesOnAndALaterDeathGetsAFullWindow() {
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, 0));
        assertEquals(Stage.LIVING, lifetime.update(false, false, NO_DEATH, SECOND));

        long later = 60 * SECOND;
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, later));
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, later + GRACE - 1));
        assertEquals(Stage.GONE, lifetime.update(false, true, NO_DEATH, later + GRACE));
    }

    @Test
    void theWindowHoldsWhenTheNanoClockWrapsAround() {
        long death = Long.MAX_VALUE - SECOND;
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, death));
        assertEquals(Stage.DYING, lifetime.update(false, true, NO_DEATH, death + 2 * SECOND));
        assertEquals(Stage.GONE, lifetime.update(false, true, NO_DEATH, death + GRACE));
    }
}
