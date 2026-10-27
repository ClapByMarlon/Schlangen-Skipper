/* Local GrieferUtils compatibility patch, licensed under Apache-2.0. */
package dev.l3g7.griefer_utils.core.injection;

import net.labymod.api.event.LabyEvent;
import net.labymod.api.event.ReplayableEvent;
import net.labymod.api.event.method.SubscribeMethodFlags;

public final class EventMethodFlags {

    private EventMethodFlags() {
    }

    public static int compute(Class<?> type, LabyEvent annotation) {
        int flags = SubscribeMethodFlags.NONE;
        if (annotation != null) {
            if (annotation.classLoaderExclusive()) flags |= SubscribeMethodFlags.CLASS_LOADER_EXCLUSIVE;
            if (annotation.background()) flags |= SubscribeMethodFlags.BACKGROUND;
        }
        if (!type.isAnnotationPresent(ReplayableEvent.class)) {
            flags |= SubscribeMethodFlags.REPLAY_DELIVERED;
        }
        return flags;
    }
}
