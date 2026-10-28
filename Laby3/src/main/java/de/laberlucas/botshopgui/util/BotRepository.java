package de.laberlucas.botshopgui.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BotRepository {

    private static final String STATIC_API_URL = "https://api.grieferutils.l3g7.dev/v6/";
    private static final long REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long MANUAL_REFRESH_COOLDOWN_MS = 5L * 60L * 1000L;
    private static final long GET_BOTS_REQUEST_INTERVAL_MS = 3000L;
    private static final Object GET_BOTS_RATE_LOCK = new Object();
    private static final Map<String, Long> NEXT_GET_BOTS_REQUEST_BY_API = new HashMap<String, Long>();
    private static long nextGlobalGetBotsRequestMs;

    private final BotUpdateListener updateListener;
    private final File cacheFile;
    private volatile List<BotInfo> bots = Collections.emptyList();
    private volatile List<SourceInfo> sources = Collections.emptyList();
    private volatile int sourceCount;
    private volatile int loadedSourceCount;
    private volatile boolean loading;
    private volatile long lastRefreshMs;
    private volatile String lastError;

    public BotRepository() {
        this(null, null);
    }

    public BotRepository(BotUpdateListener updateListener) {
        this(updateListener, null);
    }

    public BotRepository(BotUpdateListener updateListener, File cacheFile) {
        this.updateListener = updateListener;
        this.cacheFile = cacheFile;
        loadCache();
        if (!bots.isEmpty()) {
            notifyBotsUpdated();
        }
    }

    public synchronized void refreshIfNeeded(boolean force) {
        long now = System.currentTimeMillis();
        long minimumInterval = force ? MANUAL_REFRESH_COOLDOWN_MS : REFRESH_INTERVAL_MS;
        if (loading || (lastRefreshMs > 0L && now - lastRefreshMs < minimumInterval)) {
            return;
        }

        startRefresh();
    }

    public void refreshNow() {
        refreshIfNeeded(true);
    }

    private synchronized void startRefresh() {
        if (loading) {
            return;
        }

        loading = true;
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                List<String> errors = new ArrayList<String>();
                try {
                    List<BotSource> sources = deduplicateSources(loadSources(errors));
                    Map<String, BotInfo> fetched = new LinkedHashMap<String, BotInfo>();
                    List<SourceInfo> fetchedSources = new ArrayList<SourceInfo>();

                    int loadedSources = 0;
                    for (BotSource source : sources) {
                        fetchedSources.add(new SourceInfo(source.getUrl(), source.getDisplayName()));
                        try {
                            awaitGetBotsRequestSlot(source.getUrl());
                            JsonObject root = readJson(source.getUrl() + "scope/getBots");
                            if (root == null || !root.has("success") || !root.get("success").getAsBoolean()) {
                                errors.add(source.getDisplayName() + ": API meldet keinen Erfolg");
                                continue;
                            }

                            JsonArray array = root.getAsJsonArray("bots");
                            for (JsonElement element : array) {
                                String uuid = normalizeUuid(element.getAsString());
                                if (uuid.length() == 32 && source.isTrusted(uuid)) {
                                    fetched.put(uuid + "@" + source.getUrl(), new BotInfo(uuid, source.getUrl(), source.getDisplayName()));
                                }
                            }
                            loadedSources++;
                        } catch (Exception e) {
                            errors.add(source.getDisplayName() + ": " + e.getClass().getSimpleName());
                        }
                    }

                    bots = new ArrayList<BotInfo>(fetched.values());
                    BotRepository.this.sources = fetchedSources;
                    sourceCount = sources.size();
                    loadedSourceCount = loadedSources;
                    lastError = errors.isEmpty() ? null : errors.size() + " API(s) konnten nicht geladen werden";
                    notifyBotsUpdated();
                } catch (Exception e) {
                    lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    lastRefreshMs = System.currentTimeMillis();
                    writeCache();
                    loading = false;
                }
            }
        });
    }

    public boolean contains(String uuid) {
        String normalized = normalizeUuid(uuid);
        for (BotInfo bot : bots) {
            if (bot.getUuid().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getBotIds() {
        Set<String> ids = new HashSet<String>();
        for (BotInfo bot : bots) {
            ids.add(bot.getUuid());
        }
        return ids;
    }

    public List<BotInfo> getBots() {
        return new ArrayList<BotInfo>(bots);
    }

    public List<SourceInfo> getSources() {
        return new ArrayList<SourceInfo>(sources);
    }

    public int size() {
        return bots.size();
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public int getLoadedSourceCount() {
        return loadedSourceCount;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastRefreshMs() {
        return lastRefreshMs;
    }

    private void loadCache() {
        if (cacheFile == null || !cacheFile.isFile()) {
            return;
        }

        try {
            JsonObject root = readJson(new FileInputStream(cacheFile));
            List<BotInfo> cachedBots = new ArrayList<BotInfo>();
            List<SourceInfo> cachedSources = new ArrayList<SourceInfo>();

            if (root.has("bots") && root.get("bots").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("bots")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject bot = element.getAsJsonObject();
                    if (!bot.has("uuid") || !bot.has("apiUrl")) {
                        continue;
                    }

                    String uuid = normalizeUuid(bot.get("uuid").getAsString());
                    String apiUrl = bot.get("apiUrl").getAsString();
                    String apiName = bot.has("apiName") ? bot.get("apiName").getAsString() : apiUrl;
                    if (uuid.length() == 32 && !apiUrl.trim().isEmpty()) {
                        cachedBots.add(new BotInfo(uuid, apiUrl, apiName));
                    }
                }
            }

            if (root.has("sources") && root.get("sources").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("sources")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject source = element.getAsJsonObject();
                    if (!source.has("apiUrl")) {
                        continue;
                    }

                    String apiUrl = source.get("apiUrl").getAsString();
                    String apiName = source.has("apiName") ? source.get("apiName").getAsString() : apiUrl;
                    if (!apiUrl.trim().isEmpty()) {
                        cachedSources.add(new SourceInfo(apiUrl, apiName));
                    }
                }
            }

            bots = cachedBots;
            sources = cachedSources;
            sourceCount = cachedSources.size();
            loadedSourceCount = cachedSources.size();
            lastRefreshMs = root.has("lastRefreshMs") ? root.get("lastRefreshMs").getAsLong() : 0L;
            lastError = null;
        } catch (Exception ignored) {
        }
    }

    private void writeCache() {
        if (cacheFile == null) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("lastRefreshMs", lastRefreshMs);

        JsonArray serializedSources = new JsonArray();
        for (SourceInfo source : sources) {
            JsonObject serializedSource = new JsonObject();
            serializedSource.addProperty("apiUrl", source.getApiUrl());
            serializedSource.addProperty("apiName", source.getApiName());
            serializedSources.add(serializedSource);
        }
        root.add("sources", serializedSources);

        JsonArray serializedBots = new JsonArray();
        for (BotInfo bot : bots) {
            JsonObject serializedBot = new JsonObject();
            serializedBot.addProperty("uuid", bot.getUuid());
            serializedBot.addProperty("apiUrl", bot.getApiUrl());
            serializedBot.addProperty("apiName", bot.getApiName());
            serializedBots.add(serializedBot);
        }
        root.add("bots", serializedBots);

        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }

        File temporaryFile = new File(cacheFile.getPath() + ".tmp");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(temporaryFile), StandardCharsets.UTF_8);
            try {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            } finally {
                writer.close();
            }

            try {
                Files.move(
                    temporaryFile.toPath(),
                    cacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyBotsUpdated() {
        if (updateListener == null) {
            return;
        }

        try {
            updateListener.onBotsUpdated(getBotIds());
        } catch (Throwable ignored) {
        }
    }

    public static String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private List<BotSource> loadSources(List<String> errors) {
        try {
            JsonObject root = readJson(STATIC_API_URL);
            if (root != null && root.has("botSources")) {
                List<BotSource> sources = new ArrayList<BotSource>();
                for (JsonElement element : root.getAsJsonArray("botSources")) {
                    BotSource source = BotSource.fromJson(element.getAsJsonObject());
                    if (source != null) {
                        sources.add(source);
                    }
                }

                if (!sources.isEmpty()) {
                    return sources;
                }
            }
        } catch (Exception e) {
            errors.add("Static API: " + e.getClass().getSimpleName());
        }

        return fallbackSources();
    }

    private List<BotSource> deduplicateSources(List<BotSource> sourceList) {
        Map<String, BotSource> uniqueSources = new LinkedHashMap<String, BotSource>();
        for (BotSource source : sourceList) {
            String key = BotSource.normalizeUrl(source.getUrl()).toLowerCase(Locale.ROOT);
            if (!uniqueSources.containsKey(key)) {
                uniqueSources.put(key, source);
            }
        }
        return new ArrayList<BotSource>(uniqueSources.values());
    }

    private void awaitGetBotsRequestSlot(String apiUrl) throws InterruptedException {
        String normalizedApi = BotSource.normalizeUrl(apiUrl).toLowerCase(Locale.ROOT);
        synchronized (GET_BOTS_RATE_LOCK) {
            long now = System.currentTimeMillis();
            Long nextApiRequest = NEXT_GET_BOTS_REQUEST_BY_API.get(normalizedApi);
            long apiWaitUntil = nextApiRequest == null ? 0L : nextApiRequest.longValue();
            long waitUntil = Math.max(nextGlobalGetBotsRequestMs, apiWaitUntil);
            long waitMs = waitUntil - now;
            if (waitMs > 0L) {
                Thread.sleep(waitMs);
            }

            long requestTime = System.currentTimeMillis();
            nextGlobalGetBotsRequestMs = requestTime + GET_BOTS_REQUEST_INTERVAL_MS;
            NEXT_GET_BOTS_REQUEST_BY_API.put(normalizedApi, requestTime + MANUAL_REFRESH_COOLDOWN_MS);
        }
    }

    private List<BotSource> fallbackSources() {
        List<BotSource> fallback = new ArrayList<BotSource>();
        fallback.add(new BotSource("https://api.velociraptor-bot.de/api/", "HIGH", Collections.<String>emptySet()));
        fallback.add(new BotSource("https://gu.qq4.de/api/", "LOW", trustedMembers(
            "5b89ca47da3b4f948be5c4d17b0c7dc1",
            "5e4db972548d402cb1f6da75f006bfd3",
            "7bcf56c0d21041acbd18c42b789a3fba",
            "a823ccf01f1049e0b4e3813fb383d9bf",
            "64102a009f6646b08cc5b428a2e09ef5",
            "e22840f97b274bd08cbad1c6c9c94a0f"
        )));
        fallback.add(new BotSource("https://botgui.geovanni.de/", "LOW", trustedMembers(
            "7974ebdbba8b4fd5b8b280cf60062b20"
        )));
        fallback.add(new BotSource("https://blitzbot-api.de/api/grieferutils/", "LOW", trustedMembers(
            "93bbc9f9a7d246bbae9bcb0bbac1127d",
            "1eef0af2c2e84cfaa4e06e5bc54eb925"
        )));
        fallback.add(new BotSource("https://api.bewetfor.me/", "LOW", trustedMembers(
            "1a7c571cb5d34782bd50c22d55c7dadc"
        )));
        return fallback;
    }

    private Set<String> trustedMembers(String... members) {
        Set<String> result = new HashSet<String>();
        for (String member : members) {
            result.add(normalizeUuid(member));
        }
        return result;
    }

    private JsonObject readJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.addRequestProperty("User-Agent", "Schlangen-Skipper/1.0.0");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        return readJson(connection.getInputStream());
    }

    private JsonObject readJson(InputStream inputStream) throws Exception {
        StringBuilder body = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } finally {
            inputStream.close();
        }

        return new JsonParser().parse(body.toString()).getAsJsonObject();
    }

    public interface BotUpdateListener {
        void onBotsUpdated(Set<String> botIds);
    }

    public static final class BotInfo {
        private final String uuid;
        private final String apiUrl;
        private final String apiName;

        public BotInfo(String uuid, String apiUrl, String apiName) {
            this.uuid = uuid;
            this.apiUrl = apiUrl;
            this.apiName = apiName;
        }

        public String getUuid() {
            return uuid;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public String getApiName() {
            return apiName;
        }
    }

    public static final class SourceInfo {
        private final String apiUrl;
        private final String apiName;

        private SourceInfo(String apiUrl, String apiName) {
            this.apiUrl = apiUrl;
            this.apiName = apiName;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public String getApiName() {
            return apiName;
        }
    }

    private static final class BotSource {
        private final String url;
        private final String trustLevel;
        private final Set<String> trustedMembers;

        private BotSource(String url, String trustLevel, Set<String> trustedMembers) {
            this.url = normalizeUrl(url);
            this.trustLevel = trustLevel == null ? "HIGH" : trustLevel.toUpperCase(Locale.ROOT);
            this.trustedMembers = trustedMembers;
        }

        private static BotSource fromJson(JsonObject object) {
            if (object == null || !object.has("url")) {
                return null;
            }

            String url = object.get("url").getAsString();
            String trustLevel = object.has("trustLevel") ? object.get("trustLevel").getAsString() : "HIGH";
            Set<String> trustedMembers = new HashSet<String>();
            if (object.has("members") && object.get("members").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("members")) {
                    JsonObject member = element.getAsJsonObject();
                    String memberTrust = member.has("trustLevel") ? member.get("trustLevel").getAsString() : "LOW";
                    if (member.has("uuid") && !"LOW".equalsIgnoreCase(memberTrust)) {
                        trustedMembers.add(normalizeUuid(member.get("uuid").getAsString()));
                    }
                }
            }

            return new BotSource(url, trustLevel, trustedMembers);
        }

        private String getUrl() {
            return url;
        }

        private boolean isTrusted(String uuid) {
            return !"LOW".equals(trustLevel) || trustedMembers.contains(normalizeUuid(uuid));
        }

        private String getDisplayName() {
            try {
                URL parsed = new URL(url);
                String host = parsed.getHost();
                String path = parsed.getPath();
                if (path == null || path.isEmpty() || "/".equals(path)) {
                    return host;
                }
                return host + trimTrailingSlash(path);
            } catch (Exception ignored) {
                return url;
            }
        }

        private static String normalizeUrl(String value) {
            String url = value == null ? "" : value.trim();
            return url.endsWith("/") ? url : url + "/";
        }

        private static String trimTrailingSlash(String value) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }
}
