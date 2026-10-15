package de.laberlucas.botshopgui.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReadOnlyBotshopGuiTest {

    private static final int BUY_SLOT = 28;
    private static final Runnable BLOCKER = new Runnable() {
        @Override
        public void run() {
        }
    };

    @Test
    public void explicitlyOpensGuiAndBlocksAllPurchaseButtonsAfterPageRebuild() throws Exception {
        FakeGui gui = new FakeGui();
        assertFalse(gui.opened);

        ReadOnlyBotshopGui.open(gui, FakeChest.class, BUY_SLOT, BLOCKER);

        assertTrue(gui.opened);
        assertTrue(gui.blockedBeforeOpen);
        assertEquals(1, gui.openCount);
        for (List<Runnable> events : gui.callbacks()) {
            assertSame(BLOCKER, events.get(BUY_SLOT));
            events.get(BUY_SLOT).run();
            events.get(0).run();
        }
        assertEquals(0, gui.purchases);
        assertEquals(3, gui.itemSelections);
    }

    @Test
    public void reappliesProtectionAfterLaterPageChanges() throws Exception {
        FakeGui gui = new FakeGui();
        ReadOnlyBotshopGui.open(gui, FakeChest.class, BUY_SLOT, BLOCKER);
        gui.rebuildPage();

        ReadOnlyBotshopGui.blockBuying(gui, FakeChest.class, BUY_SLOT, BLOCKER);

        for (List<Runnable> events : gui.callbacks()) {
            events.get(BUY_SLOT).run();
        }
        assertEquals(0, gui.purchases);
    }

    @Test
    public void rejectsIncompatibleCallbacksBeforeOpening() throws Exception {
        FakeGui gui = new FakeGui();
        gui.callbacks().get(2).clear();
        try {
            ReadOnlyBotshopGui.open(gui, FakeChest.class, BUY_SLOT, BLOCKER);
            fail("Incompatible callbacks must prevent opening");
        } catch (IllegalStateException expected) {
            assertFalse(gui.opened);
        }
    }

    @Test
    public void leavesIndependentlyOpenedGuiUnchanged() throws Exception {
        FakeGui protectedGui = new FakeGui();
        FakeGui normalGui = new FakeGui();
        ReadOnlyBotshopGui.open(protectedGui, FakeChest.class, BUY_SLOT, BLOCKER);

        normalGui.callbacks().get(0).get(BUY_SLOT).run();

        assertEquals(1, normalGui.purchases);
        assertEquals(0, protectedGui.purchases);
    }

    public static class FakeChest {
        private final List<Runnable> leftClickEvents = new ArrayList<Runnable>();
        private final List<Runnable> rightClickEvents = new ArrayList<Runnable>();
        private final List<Runnable> midClickEvents = new ArrayList<Runnable>();
        int purchases;
        int itemSelections;

        FakeChest() {
            rebuildPage();
        }

        List<List<Runnable>> callbacks() {
            return Arrays.asList(leftClickEvents, rightClickEvents, midClickEvents);
        }

        void rebuildPage() {
            for (List<Runnable> events : callbacks()) {
                events.clear();
                for (int slot = 0; slot < 63; slot++) {
                    events.add(null);
                }
                events.set(BUY_SLOT, new Runnable() {
                    @Override
                    public void run() {
                        purchases++;
                    }
                });
                events.set(0, new Runnable() {
                    @Override
                    public void run() {
                        itemSelections++;
                    }
                });
            }
        }
    }

    public static final class FakeGui extends FakeChest {
        boolean opened;
        boolean blockedBeforeOpen;
        int openCount;

        public void open() {
            blockedBeforeOpen = callbacks().get(0).get(BUY_SLOT) == BLOCKER;
            opened = true;
            openCount++;
            rebuildPage();
        }
    }
}
