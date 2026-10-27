package de.laberlucas.botshopgui.gui;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScopedVisibilityTest {

    @Test
    public void hidesAndRestoresOriginallyVisibleOverlay() {
        Element element = new Element(true);
        ScopedVisibility<Element> visibility = scope();
        visibility.hide(element);
        assertFalse(element.visible);
        visibility.restore();
        assertTrue(element.visible);
    }

    @Test
    public void repeatedRenderPassesAndResizePreserveOriginalStates() {
        Element original = new Element(true);
        Element replacement = new Element(false);
        ScopedVisibility<Element> visibility = scope();
        visibility.hide(original);
        visibility.hide(original);
        original.visible = true;
        visibility.hide(original);
        visibility.hide(replacement);
        assertFalse(original.visible);
        visibility.restore();
        assertTrue(original.visible);
        assertFalse(replacement.visible);
    }

    @Test
    public void closeIsIdempotentAndReopeningCapturesNewState() {
        Element element = new Element(true);
        ScopedVisibility<Element> visibility = scope();
        visibility.hide(element);
        visibility.restore();
        element.visible = false;
        visibility.restore();
        assertFalse(element.visible);
        visibility.hide(element);
        visibility.restore();
        assertFalse(element.visible);
    }

    private static ScopedVisibility<Element> scope() {
        return new ScopedVisibility<>(element -> element.visible, (element, visible) -> element.visible = visible);
    }

    private static class Element {
        private boolean visible;

        private Element(boolean visible) {
            this.visible = visible;
        }
    }
}
