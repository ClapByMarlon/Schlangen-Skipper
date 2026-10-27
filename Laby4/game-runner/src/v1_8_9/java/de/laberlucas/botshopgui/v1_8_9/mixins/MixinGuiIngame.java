package de.laberlucas.botshopgui.v1_8_9.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {

    // GuiScreen relies on the HUD's 2D projection, even when the HUD is hidden.
    @Inject(method = "renderGameOverlay", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;setupOverlayRendering()V",
        shift = At.Shift.AFTER), cancellable = true)
    private void schlangenSkipper$hideBrowserHud(float partialTicks, CallbackInfo ci) {
        Object screen = Minecraft.getMinecraft().currentScreen;
        // Use the name because this code runs in Minecraft's, not the addon's classloader.
        if (screen != null && screen.getClass().getName().equals(
            "de.laberlucas.botshopgui.gui.Laby4BotSelectionGui")) {
            ci.cancel();
        }
    }
}
