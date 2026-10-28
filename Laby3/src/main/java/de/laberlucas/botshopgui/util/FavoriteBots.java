package de.laberlucas.botshopgui.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.laberlucas.botshopgui.util.BotRepository.BotInfo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FavoriteBots {

    private final Path file;
    private Map<String, BotInfo> favorites = new LinkedHashMap<String, BotInfo>();
    private boolean favoritesOnly;
    private boolean readFailed;

    public FavoriteBots(File file) {
        this.file = file.toPath().toAbsolutePath();
        load();
    }

    public synchronized boolean contains(String uuid) {
        return favorites.containsKey(BotRepository.normalizeUuid(uuid));
    }

    public synchronized boolean isFavoritesOnly() {
        return favoritesOnly;
    }

    public synchronized boolean hasReadFailed() {
        return readFailed;
    }

    public synchronized List<String> getIds() {
        return new ArrayList<String>(favorites.keySet());
    }

    public synchronized void toggle(BotInfo bot) throws IOException {
        String uuid = BotRepository.normalizeUuid(bot.getUuid());
        if (!validUuid(uuid) || !validApi(bot.getApiUrl())) {
            throw new IOException("Invalid favorite bot");
        }
        Map<String, BotInfo> next = new LinkedHashMap<String, BotInfo>(favorites);
        if (next.containsKey(uuid)) {
            next.remove(uuid);
        } else {
            next.put(uuid, new BotInfo(uuid, bot.getApiUrl(),
                bot.getApiName() == null ? bot.getApiUrl() : bot.getApiName()));
        }
        save(next, favoritesOnly);
        favorites = next;
    }

    public synchronized void setFavoritesOnly(boolean value) throws IOException {
        if (favoritesOnly != value) {
            save(favorites, value);
            favoritesOnly = value;
        }
    }

    public synchronized List<BotInfo> getBots(List<BotInfo> currentBots) {
        Map<String, BotInfo> result = new LinkedHashMap<String, BotInfo>(favorites);
        for (BotInfo bot : currentBots) {
            String uuid = BotRepository.normalizeUuid(bot.getUuid());
            BotInfo saved = favorites.get(uuid);
            if (saved != null && saved.getApiUrl().equals(bot.getApiUrl())) {
                result.put(uuid, bot);
            }
        }
        return new ArrayList<BotInfo>(result.values());
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            if (Files.size(file) > 1024 * 1024) {
                throw new IOException("Favorites file exceeds size limit");
            }
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
            Map<String, BotInfo> loaded = new LinkedHashMap<String, BotInfo>();
            for (JsonElement entry : root.getAsJsonArray("favorites")) {
                JsonObject bot = entry.getAsJsonObject();
                String uuid = BotRepository.normalizeUuid(bot.get("uuid").getAsString());
                String api = bot.get("apiUrl").getAsString();
                if (validUuid(uuid) && validApi(api)) {
                    String name = bot.has("apiName") && !bot.get("apiName").isJsonNull()
                        ? bot.get("apiName").getAsString() : api;
                    loaded.put(uuid, new BotInfo(uuid, api, name));
                }
            }
            favorites = loaded;
            favoritesOnly = root.has("favoritesOnly") && root.get("favoritesOnly").getAsBoolean();
        } catch (Exception failure) {
            // Never overwrite an unreadable favorites file with an empty list.
            readFailed = true;
        }
    }

    private void save(Map<String, BotInfo> bots, boolean only) throws IOException {
        if (readFailed) {
            throw new IOException("Favorites could not be read; preserving original file");
        }
        JsonObject root = new JsonObject();
        root.addProperty("favoritesOnly", only);
        JsonArray array = new JsonArray();
        for (BotInfo bot : bots.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", bot.getUuid());
            entry.addProperty("apiUrl", bot.getApiUrl());
            entry.addProperty("apiName", bot.getApiName());
            array.add(entry);
        }
        root.add("favorites", array);
        byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) {
            throw new IOException("Favorites file exceeds size limit");
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".schlangen-skipper-favorites-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean validUuid(String uuid) {
        return uuid.matches("[0-9a-f]{32}");
    }

    private static boolean validApi(String api) {
        try {
            URI uri = new URI(api);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
