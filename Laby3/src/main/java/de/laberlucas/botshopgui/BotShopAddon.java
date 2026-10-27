package de.laberlucas.botshopgui;

import de.laberlucas.botshopgui.feature.ClientTickHandler;
import de.laberlucas.botshopgui.gui.BotSelectionGui;
import de.laberlucas.botshopgui.settings.BotShopSettings;
import de.laberlucas.botshopgui.util.BotNameResolver;
import de.laberlucas.botshopgui.util.BotRepository.BotInfo;
import de.laberlucas.botshopgui.util.BotRepository.BotUpdateListener;
import de.laberlucas.botshopgui.util.BotRepository;
import de.laberlucas.botshopgui.util.GrieferUtilsBridge;
import de.laberlucas.botshopgui.update.AddonAutoUpdater;
import net.labymod.addon.AddonLoader;
import net.labymod.api.LabyModAddon;
import net.labymod.settings.elements.SettingsElement;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class BotShopAddon extends LabyModAddon {

    private static BotShopAddon instance;
    private static final AtomicBoolean UPDATE_CHECK_STARTED = new AtomicBoolean();
    private volatile String updateStatus;

    private BotShopSettings settings;
    private BotRepository botRepository;
    private BotNameResolver botNameResolver;
    private GrieferUtilsBridge grieferUtilsBridge;
    private final ClientTickHandler tickHandler = new ClientTickHandler(this);

    public BotShopAddon() {
        instance = this;
    }

    @Override
    public void onEnable() {
        instance = this;
        ensureSettings();

        botNameResolver = new BotNameResolver(getConfig(), getBotNameCacheFile());
        botRepository = new BotRepository(new BotUpdateListener() {
            @Override
            public void onBotsUpdated(Set<String> botIds) {
                prefetchBotNames(botIds);
            }
        }, getBotListCacheFile());
        grieferUtilsBridge = new GrieferUtilsBridge(this);
        botRepository.refreshIfNeeded(false);
        settings.init();

        FMLCommonHandler.instance().bus().register(tickHandler);
        MinecraftForge.EVENT_BUS.register(tickHandler);
        startUpdateCheck();
    }

    private void startUpdateCheck() {
        if (!settings.isAutoUpdateEnabled() || !UPDATE_CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File currentJar = AddonLoader.getJarFileByClass(BotShopAddon.class);
                    if (currentJar == null) {
                        return;
                    }
                    String version = new AddonAutoUpdater().checkAndInstall(
                        currentJar, AddonLoader.getDeleteQueueFile()
                    );
                    if (version != null) {
                        updateStatus = "\u00a7aUpdate " + version + " geladen - Neustart erforderlich.";
                    }
                } catch (Exception exception) {
                    Logger.getLogger("Schlangen Skipper").warning(
                        "Update check failed (" + exception.getClass().getSimpleName() + "). Keeping installed version."
                    );
                }
            }
        }, "Schlangen Skipper Update");
        worker.setDaemon(true);
        worker.start();
    }

    public String getUpdateStatus() {
        return updateStatus;
    }

    @Override
    public void onDisable() {
        FMLCommonHandler.instance().bus().unregister(tickHandler);
        MinecraftForge.EVENT_BUS.unregister(tickHandler);
        if (botNameResolver != null) {
            botNameResolver.writePersistentCache(null, true);
            botNameResolver.shutdown();
        }
    }

    @Override
    public void loadConfig() {
        ensureSettings();
        settings.loadFromConfig(getConfig());
        if (botNameResolver != null) {
            botNameResolver.loadPersistentCache(getConfig());
        }
    }

    @Override
    protected void fillSettings(List<SettingsElement> list) {
        ensureSettings();
        settings.fillSettings(list);
    }

    public void openAllBotsGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }

        if (botRepository != null) {
            botRepository.refreshIfNeeded(false);
        }

        mc.displayGuiScreen(new BotSelectionGui(this));
    }

    public void openBotshop(BotInfo botInfo) {
        if (grieferUtilsBridge != null && botInfo != null) {
            grieferUtilsBridge.openBotshop(botInfo);
        }
    }

    public String getBotshopStatus() {
        return grieferUtilsBridge == null ? null : grieferUtilsBridge.getStatus();
    }

    public boolean protectBotshopInput(Object screen) {
        return grieferUtilsBridge != null && grieferUtilsBridge.protectBotshopInput(screen);
    }

    public String getBotDisplayName(String uuid) {
        if (grieferUtilsBridge != null) {
            String grieferUtilsName = grieferUtilsBridge.getBotDisplayName(uuid);
            if (grieferUtilsName != null) {
                return grieferUtilsName;
            }
        }

        return botNameResolver == null ? null : botNameResolver.getName(uuid);
    }

    public void prefetchBotNames(Iterable<String> uuids) {
        if (botNameResolver != null) {
            botNameResolver.prefetch(uuids);
        }
    }

    public void tickClient() {
        if (grieferUtilsBridge != null) {
            grieferUtilsBridge.protectActiveBotshop();
        }
        if (botRepository != null) {
            botRepository.refreshIfNeeded(false);
        }
        if (botNameResolver != null) {
            botNameResolver.tick();
            botNameResolver.writePersistentCache(null, false);
        }
    }

    public void notify(String message) {
    }

    private void ensureSettings() {
        if (settings == null) {
            settings = new BotShopSettings();
        }
    }

    private File getBotNameCacheFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File gameDirectory = mc == null ? new File(".") : mc.mcDataDir;
        return new File(gameDirectory, "LabyMod/addons-1.8/config/schlangen-skipper-names.json");
    }

    private File getBotListCacheFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File gameDirectory = mc == null ? new File(".") : mc.mcDataDir;
        return new File(gameDirectory, "LabyMod/addons-1.8/config/schlangen-skipper-bots.json");
    }

    public static BotShopAddon getInstance() {
        return instance;
    }

    public BotShopSettings getSettings() {
        ensureSettings();
        return settings;
    }

    public BotRepository getBotRepository() {
        return botRepository;
    }
}
