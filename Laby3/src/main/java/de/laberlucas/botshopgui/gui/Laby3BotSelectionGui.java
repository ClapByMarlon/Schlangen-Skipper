package de.laberlucas.botshopgui.gui;

import de.laberlucas.botshopgui.BotShopAddon;
import de.laberlucas.botshopgui.util.BotRepository.BotInfo;
import de.laberlucas.botshopgui.util.FavoriteBots;

import java.io.IOException;
import java.util.List;

public final class Laby3BotSelectionGui extends BotSelectionGui {

    private final FavoriteBots favorites;

    public Laby3BotSelectionGui(BotShopAddon addon, FavoriteBots favorites) {
        super(addon);
        this.favorites = favorites;
    }

    @Override
    protected boolean favoritesEnabled() {
        return true;
    }

    @Override
    protected boolean isFavorite(String uuid) {
        return favorites.contains(uuid);
    }

    @Override
    protected void toggleFavorite(BotInfo bot) throws IOException {
        favorites.toggle(bot);
    }

    @Override
    protected boolean initialFavoritesOnly() {
        return favorites.isFavoritesOnly();
    }

    @Override
    protected void saveFavoritesView(boolean only) throws IOException {
        favorites.setFavoritesOnly(only);
    }

    @Override
    protected List<BotInfo> getViewBots(List<BotInfo> current, boolean only) {
        return only ? favorites.getBots(current) : current;
    }

    @Override
    protected String getFavoritesLoadError() {
        return favorites.hasReadFailed() ? "\u00a7cFavoriten konnten nicht geladen werden." : null;
    }
}
