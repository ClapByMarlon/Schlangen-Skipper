package de.laberlucas.botshopgui.gui;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class HudVisibilityTest {

    @Test
    public void hidesHudUntilBrowserCloses() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        HudVisibility hud = new HudVisibility(hidden::get, hidden::set);
        hud.hide();
        assertTrue(hidden.get());
        hud.restore();
        assertFalse(hidden.get());
    }

    @Test
    public void resizingAndRenderingDoNotOverwriteOriginalVisibility() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        HudVisibility hud = new HudVisibility(hidden::get, hidden::set);
        hud.hide();
        hud.hide();
        hidden.set(false);
        hud.hide();
        assertTrue(hidden.get());
        hud.restore();
        assertFalse(hidden.get());
    }

    @Test
    public void preservesHudAlreadyHiddenByF1() {
        AtomicBoolean hidden = new AtomicBoolean(true);
        HudVisibility hud = new HudVisibility(hidden::get, hidden::set);
        hud.hide();
        hud.hide();
        hud.restore();
        assertTrue(hidden.get());
    }

    @Test
    public void repeatedCloseDoesNotChangeHudAndReopenCapturesNewState() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        HudVisibility hud = new HudVisibility(hidden::get, hidden::set);
        hud.restore();
        assertFalse(hidden.get());
        hud.hide();
        hud.restore();
        hidden.set(true);
        hud.restore();
        assertTrue(hidden.get());
        hud.hide();
        hud.restore();
        assertTrue(hidden.get());
    }
}
