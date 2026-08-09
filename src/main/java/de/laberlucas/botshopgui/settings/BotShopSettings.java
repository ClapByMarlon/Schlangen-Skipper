package de.laberlucas.botshopgui.settings;

import com.google.gson.JsonObject;
import de.laberlucas.botshopgui.BotShopAddon;
import net.labymod.settings.elements.BooleanElement;
import net.labymod.settings.elements.ControlElement;
import net.labymod.settings.elements.KeyElement;
import net.labymod.settings.elements.ListContainerElement;
import net.labymod.settings.elements.SettingsElement;
import net.labymod.utils.Consumer;
import net.labymod.utils.Material;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public class BotShopSettings {

    private static final String ENABLED = "botshopGuiEnabled";
    private static final String OPEN_KEY = "botshopGuiOpenKey";
    private static final String ALL_BOTS_KEY = "botshopGuiAllBotsKey";

    private boolean enabled = true;
    private int openKey = Keyboard.KEY_RSHIFT;
    private boolean openKeyWasDown;

    public void init() {
        openKeyWasDown = false;
    }

    public void loadFromConfig(JsonObject config) {
        if (config == null) {
            return;
        }

        enabled = readBoolean(config, ENABLED, enabled);
        openKey = readInt(config, OPEN_KEY, openKey);
        openKey = readInt(config, ALL_BOTS_KEY, openKey);
    }

    public void fillSettings(List<SettingsElement> list) {
        final BotShopAddon addon = BotShopAddon.getInstance();

        ListContainerElement botshop = new ListContainerElement(
            "Schlangen Skipper",
            new ControlElement.IconData(Material.CHEST)
        );
        list.add(botshop);

        botshop.getSubSettings().add(new BooleanElement(
            "Schlangen Skipper aktiv",
            addon,
            new ControlElement.IconData(Material.CHEST),
            ENABLED,
            enabled
        ).addCallback(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean value) {
                enabled = value.booleanValue();
                writeBoolean(ENABLED, enabled);
            }
        }));

        botshop.getSubSettings().add(new KeyElement(
            "Öffnen Taste",
            addon,
            new ControlElement.IconData(Material.REDSTONE),
            OPEN_KEY,
            openKey,
            true
        ).addCallback(new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                openKey = value.intValue();
                writeInt(OPEN_KEY, openKey);
            }
        }));
    }

    public boolean consumeOpenPress() {
        boolean down = isConfiguredKeyDown(enabled, openKey);
        boolean pressed = down && !openKeyWasDown;
        openKeyWasDown = down;
        return pressed;
    }

    private boolean isConfiguredKeyDown(boolean settingEnabled, int key) {
        if (!settingEnabled || key == Keyboard.KEY_NONE) {
            return false;
        }
        if (key < 0) {
            int mouseButton = key + 100;
            return mouseButton >= 0 && Mouse.isButtonDown(mouseButton);
        }
        return Keyboard.isKeyDown(key);
    }

    private boolean readBoolean(JsonObject config, String key, boolean fallback) {
        try {
            return config.has(key) ? config.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int readInt(JsonObject config, String key, int fallback) {
        try {
            return config.has(key) ? config.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private void writeBoolean(String key, boolean value) {
        BotShopAddon addon = BotShopAddon.getInstance();
        if (addon != null && addon.getConfig() != null) {
            addon.getConfig().addProperty(key, value);
            addon.saveConfig();
        }
    }

    private void writeInt(String key, int value) {
        BotShopAddon addon = BotShopAddon.getInstance();
        if (addon != null && addon.getConfig() != null) {
            addon.getConfig().addProperty(key, value);
            addon.saveConfig();
        }
    }
}
