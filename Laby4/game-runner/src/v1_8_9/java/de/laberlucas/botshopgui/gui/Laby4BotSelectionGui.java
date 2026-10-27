package de.laberlucas.botshopgui.gui;

import de.laberlucas.botshopgui.BotShopAddon;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

public final class Laby4BotSelectionGui extends BotSelectionGui {

    private final BotShopAddon addon;

    public Laby4BotSelectionGui(BotShopAddon addon) {
        super(addon);
        this.addon = addon;
    }

    @Override
    public void initGui() {
        addon.hideBrowserOverlays();
        try {
            super.initGui();
        } catch (RuntimeException | Error failure) {
            addon.restoreBrowserOverlays();
            throw failure;
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            super.onGuiClosed();
        } finally {
            addon.restoreBrowserOverlays();
        }
    }

    @Override
    protected GuiTextField createSearchField(FontRenderer font, int x, int y, int width, int height) {
        return new BrowserSearchField(font, x, y, width, height);
    }
}
