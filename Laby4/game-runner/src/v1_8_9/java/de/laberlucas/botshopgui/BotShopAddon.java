package de.laberlucas.botshopgui;

import de.laberlucas.botshopgui.gui.Laby4BotSelectionGui;
import de.laberlucas.botshopgui.gui.ScopedVisibility;
import de.laberlucas.botshopgui.util.BotNameResolver;
import de.laberlucas.botshopgui.util.BotRepository;
import de.laberlucas.botshopgui.util.GrieferUtilsBridge;
import de.laberlucas.botshopgui.update.AddonAutoUpdater;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.MouseButtonEvent;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.client.render.GameRenderEvent;
import net.labymod.api.event.client.render.ScreenRenderEvent;
import net.labymod.api.event.client.render.overlay.IngameOverlayRenderEvent;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

@AddonMain
public final class BotShopAddon extends LabyAddon<BotShopConfiguration> {

    private BotRepository repository;
    private BotNameResolver names;
    private GrieferUtilsBridge grieferUtils;
    private boolean openKeyWasDown;
    private static final AtomicBoolean UPDATE_CHECK_STARTED = new AtomicBoolean();
    private volatile String updateStatus;
    private final ScopedVisibility<Document> overlays = new ScopedVisibility<>(
        Document::isVisible, Document::setVisible);

    @Override
    protected void enable() {
        registerSettingCategory();
        Laby.references().widgetConverterRegistry().exclude(Laby4BotSelectionGui.class);
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
        startUpdateCheck();
    }

    private void startUpdateCheck() {
        if (!configuration().autoUpdate().get() || !UPDATE_CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                File currentJar = addonInfo().getPath().toFile();
                String version = new AddonAutoUpdater(AddonAutoUpdater.Platform.LABY4)
                    .checkAndInstall(currentJar, null);
                if (version != null) {
                    updateStatus = "\u00a7aUpdate " + version + " geladen - Neustart erforderlich.";
                }
            } catch (Exception exception) {
                java.util.logging.Logger.getLogger("Schlangen Skipper").warning(
                    "Update check failed (" + exception.getClass().getSimpleName()
                        + "). Keeping installed version.");
            }
        }, "Schlangen Skipper Laby4 Update");
        worker.setDaemon(true);
        worker.start();
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

    @Subscribe(Priority.FIRST)
    public void onGameRender(GameRenderEvent event) {
        if (event.phase() == Phase.PRE) {
            updateBrowserOverlays();
        }
    }

    @Subscribe(Priority.FIRST)
    public void onOverlayRender(IngameOverlayRenderEvent event) {
        updateBrowserOverlays();
    }

    @Subscribe(Priority.FIRST)
    public void onScreenRender(ScreenRenderEvent event) {
        updateBrowserOverlays();
    }

    private void updateBrowserOverlays() {
        if (Minecraft.getMinecraft().currentScreen instanceof Laby4BotSelectionGui) {
            hideBrowserOverlays();
        } else {
            restoreBrowserOverlays();
        }
    }

    public void hideBrowserOverlays() {
        // Hide only the UI documents, not chat reception, widget ticking or user settings.
        for (var activity : labyAPI().ingameOverlay().getActivities()) {
            overlays.hide(activity.document());
        }
    }

    public void restoreBrowserOverlays() {
        overlays.restore();
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
        return updateStatus;
    }

    public void prefetchBotNames(Iterable<String> uuids) {
        names.prefetch(uuids);
    }
}
