package de.laberlucas.botshopgui;

import de.laberlucas.botshopgui.gui.Laby4BotSelectionGui;
import de.laberlucas.botshopgui.util.BotNameResolver;
import de.laberlucas.botshopgui.util.BotRepository;
import de.laberlucas.botshopgui.util.GrieferUtilsBridge;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.MouseButtonEvent;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.minecraft.client.Minecraft;

import java.io.File;

@AddonMain
public final class BotShopAddon extends LabyAddon<BotShopConfiguration> {

    private BotRepository repository;
    private BotNameResolver names;
    private GrieferUtilsBridge grieferUtils;
    private boolean openKeyWasDown;

    @Override
    protected void enable() {
        registerSettingCategory();
        File cacheDirectory = new File(Minecraft.getMinecraft().mcDataDir,
            "labymod-neo/configs/schlangen_skipper/cache");
        names = new BotNameResolver(null, new File(cacheDirectory, "names.json"));
        repository = new BotRepository(names::prefetch, new File(cacheDirectory, "bots.json"));
        grieferUtils = new GrieferUtilsBridge(this);
        registerListener(this);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            names.writePersistentCache(null, true);
            names.shutdown();
        }, "Schlangen Skipper Laby4 Shutdown"));
        if (configuration().enabled().get()) {
            repository.refreshIfNeeded(false);
        }
    }

    @Override
    protected Class<BotShopConfiguration> configurationClass() {
        return BotShopConfiguration.class;
    }

    @Subscribe
    public void onTick(GameTickEvent event) {
        if (event.phase() != Phase.POST) {
            return;
        }
        // Keep an already opened read-only shop protected even after disabling the addon.
        grieferUtils.protectActiveBotshop();
        boolean down = configuration().openKey().get().isPressed();
        boolean pressed = down && !openKeyWasDown;
        openKeyWasDown = down;
        if (!configuration().enabled().get()) {
            return;
        }
        repository.refreshIfNeeded(false);
        names.tick();
        names.writePersistentCache(null, false);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (pressed && minecraft.thePlayer != null && minecraft.theWorld != null
            && minecraft.currentScreen == null) {
            minecraft.displayGuiScreen(new Laby4BotSelectionGui(this));
        }
    }

    @Subscribe(Priority.LATEST)
    public void onMouseButton(MouseButtonEvent event) {
        if (grieferUtils.protectBotshopInput(Minecraft.getMinecraft().currentScreen)) {
            event.setCancelled(true);
        }
    }

    public BotRepository getBotRepository() {
        return repository;
    }

    public void openBotshop(BotRepository.BotInfo bot) {
        if (configuration().enabled().get()) {
            grieferUtils.openBotshop(bot);
        }
    }

    public String getBotDisplayName(String uuid) {
        String name = grieferUtils.getBotDisplayName(uuid);
        return name == null ? names.getName(uuid) : name;
    }

    public String getBotshopStatus() {
        return grieferUtils.getStatus();
    }

    public String getUpdateStatus() {
        return null;
    }

    public void prefetchBotNames(Iterable<String> uuids) {
        names.prefetch(uuids);
    }
}
