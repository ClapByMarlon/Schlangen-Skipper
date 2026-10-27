package de.laberlucas.botshopgui.gui;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class ScopedVisibility<T> {

    private final Map<T, Boolean> previous = new IdentityHashMap<>();
    private final Predicate<T> isVisible;
    private final BiConsumer<T, Boolean> setVisible;

    public ScopedVisibility(Predicate<T> isVisible, BiConsumer<T, Boolean> setVisible) {
        this.isVisible = isVisible;
        this.setVisible = setVisible;
    }

    public void hide(T element) {
        if (!previous.containsKey(element)) {
            previous.put(element, isVisible.test(element));
        }
        if (isVisible.test(element)) {
            setVisible.accept(element, false);
        }
    }

    public void restore() {
        for (Map.Entry<T, Boolean> entry : previous.entrySet()) {
            if (isVisible.test(entry.getKey()) != entry.getValue()) {
                setVisible.accept(entry.getKey(), entry.getValue());
            }
        }
        previous.clear();
    }
}
