package de.laberlucas.botshopgui.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class HudVisibility {

    private final BooleanSupplier hidden;
    private final Consumer<Boolean> setHidden;
    private boolean active;
    private boolean previouslyHidden;

    HudVisibility(BooleanSupplier hidden, Consumer<Boolean> setHidden) {
        this.hidden = hidden;
        this.setHidden = setHidden;
    }

    void hide() {
        // initGui is also called on resize; capture the original state only once.
        if (!active) {
            previouslyHidden = hidden.getAsBoolean();
            active = true;
        }
        setHidden.accept(true);
    }

    void restore() {
        if (active) {
            setHidden.accept(previouslyHidden);
            active = false;
        }
    }
}
