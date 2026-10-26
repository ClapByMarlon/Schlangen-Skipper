package dev.l3g7.griefer_utils.core.injection;

import net.labymod.api.event.LabyEvent;
import net.labymod.api.event.ReplayableEvent;
import org.junit.Test;

import static org.junit.Assert.*;

public class EventMethodFlagsTest {

    @Test
    public void normalEventNeedsNoReplay() {
        assertEquals(4, EventMethodFlags.compute(Object.class, null));
    }

    @Test
    public void preservesBackgroundAndClassLoaderExclusiveFlags() {
        assertEquals(7, EventMethodFlags.compute(Marked.class, Marked.class.getAnnotation(LabyEvent.class)));
    }

    @Test
    public void replayableEventIsNotAlreadyDelivered() {
        assertEquals(0, EventMethodFlags.compute(Replayable.class, null));
    }

    @LabyEvent(background = true, classLoaderExclusive = true, allowAllExceptions = false, allowExceptions = {})
    private static class Marked {}

    @ReplayableEvent(once = true)
    private static class Replayable {}
}
