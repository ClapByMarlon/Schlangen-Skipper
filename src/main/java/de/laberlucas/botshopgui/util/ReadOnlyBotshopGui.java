package de.laberlucas.botshopgui.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

final class ReadOnlyBotshopGui {

    private ReadOnlyBotshopGui() {
    }

    static void open(Object gui, Class<?> callbackOwner, int buySlot, Runnable blocker)
        throws ReflectiveOperationException {
        blockBuying(gui, callbackOwner, buySlot, blocker);
        gui.getClass().getMethod("open").invoke(gui);
        // open() rebuilds the page in recent GrieferUtils versions.
        blockBuying(gui, callbackOwner, buySlot, blocker);
    }

    @SuppressWarnings("unchecked")
    static void blockBuying(Object gui, Class<?> callbackOwner, int buySlot, Runnable blocker)
        throws ReflectiveOperationException {
        List<List<Runnable>> callbacks = new ArrayList<List<Runnable>>();
        for (String name : new String[]{"leftClickEvents", "rightClickEvents", "midClickEvents"}) {
            Field field = callbackOwner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(gui);
            if (!(value instanceof List) || buySlot < 0 || ((List<?>) value).size() <= buySlot) {
                throw new IllegalStateException("Unsupported GrieferUtils buy callbacks");
            }
            callbacks.add((List<Runnable>) value);
        }
        for (List<Runnable> events : callbacks) {
            events.set(buySlot, blocker);
        }
    }
}
