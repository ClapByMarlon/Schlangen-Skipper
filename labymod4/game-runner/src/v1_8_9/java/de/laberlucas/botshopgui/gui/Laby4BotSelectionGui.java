package de.laberlucas.botshopgui.gui;

import de.laberlucas.botshopgui.BotShopAddon;
import net.minecraft.client.Minecraft;

public final class Laby4BotSelectionGui extends BotSelectionGui {

    private final HudVisibility hud = new HudVisibility(
        () -> Minecraft.getMinecraft().gameSettings.hideGUI,
        hidden -> Minecraft.getMinecraft().gameSettings.hideGUI = hidden
    );

    public Laby4BotSelectionGui(BotShopAddon addon) {
        super(addon);
    }

    @Override
    public void initGui() {
        hud.hide();
        try {
            super.initGui();
        } catch (RuntimeException | Error failure) {
            hud.restore();
            throw failure;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hud.hide();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        try {
            super.onGuiClosed();
        } finally {
            hud.restore();
        }
    }
}
