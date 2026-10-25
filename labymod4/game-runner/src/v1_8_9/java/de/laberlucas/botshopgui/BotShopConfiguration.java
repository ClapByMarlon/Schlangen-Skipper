package de.laberlucas.botshopgui;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("settings")
public final class BotShopConfiguration extends AddonConfig {

    @SwitchSetting
    private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

    @KeyBindSetting(acceptMouseButtons = true)
    private final ConfigProperty<Key> openKey = new ConfigProperty<>(Key.R_SHIFT);

    @Override
    public ConfigProperty<Boolean> enabled() {
        return enabled;
    }

    public ConfigProperty<Key> openKey() {
        return openKey;
    }
}
