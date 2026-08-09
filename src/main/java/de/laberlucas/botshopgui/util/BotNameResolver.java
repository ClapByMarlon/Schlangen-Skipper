package de.laberlucas.botshopgui.util;

import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class BotNameResolver {

    private static final String MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String ASHCON_PROFILE_URL = "https://api.ashcon.app/mojang/v2/user/";
    private static final String CACHE_KEY = "botNameCache";
    private static final long INITIAL_RETRY_DELAY_MS = 30000L;
    private static final long MAX_RETRY_DELAY_MS = 60L * 60L * 1000L;
    private static final long PROFILE_REQUEST_INTERVAL_MS = 750L;
    private static final long RETRY_SCAN_INTERVAL_MS = 1000L;
    private static final long CACHE_WRITE_INTERVAL_MS = 2000L;
    private static final int LOOKUP_THREADS = 1;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final ConcurrentMap<String, String> names = new ConcurrentHashMap<String, String>();
    private final ConcurrentMap<String, Integer> failureCounts = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentMap<String, Long> retryAfter = new ConcurrentHashMap<String, Long>();
    private final Set<String> queued = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> knownUuids = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Object cacheLock = new Object();
    private final Object requestRateLock = new Object();
    private final File cacheFile;
    private final ExecutorService executor = Executors.newFixedThreadPool(LOOKUP_THREADS, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Schlangen-Skipper-NameResolver-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile boolean cacheDirty;
    private volatile long nextCacheWriteMs;
    private volatile long nextRetryScanMs;
    private long nextProfileRequestMs;

    public BotNameResolver() {
        this(null, null);
    }

    public BotNameResolver(JsonObject config) {
        this(config, null);
    }

    public BotNameResolver(JsonObject config, File cacheFile) {
        this.cacheFile = cacheFile;
        loadFileCache();
        loadPersistentCache(config);
    }

    public String getName(String uuid) {
        String normalized = BotRepository.normalizeUuid(uuid);
        remember(normalized);
        String cached = names.get(normalized);
        if (cached != null) {
            return cached;
        }

        queue(normalized);
        return null;
    }

    public void prefetch(Iterable<String> uuids) {
        if (uuids == null) {
            return;
        }

        for (String uuid : uuids) {
            String normalized = BotRepository.normalizeUuid(uuid);
            remember(normalized);
            queue(normalized);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now < nextRetryScanMs) {
            return;
        }

        nextRetryScanMs = now + RETRY_SCAN_INTERVAL_MS;
        for (String uuid : knownUuids) {
            queue(uuid);
        }
    }

    public void loadPersistentCache(JsonObject config) {
        if (config == null || !config.has(CACHE_KEY) || !config.get(CACHE_KEY).isJsonObject()) {
            return;
        }

        synchronized (cacheLock) {
            loadCacheEntries(config.getAsJsonObject(CACHE_KEY));
        }
    }

    public boolean writePersistentCache(JsonObject config, boolean force) {
        if ((config == null && cacheFile == null) || !cacheDirty) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (!force && now < nextCacheWriteMs) {
            return false;
        }

        synchronized (cacheLock) {
            if (!cacheDirty) {
                return false;
            }

            JsonObject serializedNames = new JsonObject();
            for (Map.Entry<String, String> entry : names.entrySet()) {
                serializedNames.addProperty(entry.getKey(), entry.getValue());
            }

            boolean persisted = false;
            if (config != null) {
                config.add(CACHE_KEY, serializedNames);
                persisted = true;
            }
            if (cacheFile != null) {
                persisted = writeFileCache(serializedNames) || persisted;
            }
            if (!persisted) {
                nextCacheWriteMs = now + CACHE_WRITE_INTERVAL_MS;
                return false;
            }

            cacheDirty = false;
            nextCacheWriteMs = now + CACHE_WRITE_INTERVAL_MS;
            return true;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void remember(String uuid) {
        if (uuid.length() == 32) {
            knownUuids.add(uuid);
        }
    }

    private void loadFileCache() {
        if (cacheFile == null || !cacheFile.isFile()) {
            return;
        }

        try {
            JsonObject root = readJsonObject(new FileInputStream(cacheFile));
            JsonObject serializedNames = root.has("names") && root.get("names").isJsonObject()
                ? root.getAsJsonObject("names")
                : root;
            synchronized (cacheLock) {
                loadCacheEntries(serializedNames);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadCacheEntries(JsonObject serializedNames) {
        for (Map.Entry<String, JsonElement> entry : serializedNames.entrySet()) {
            String uuid = BotRepository.normalizeUuid(entry.getKey());
            JsonElement value = entry.getValue();
            if (uuid.length() != 32 || value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                continue;
            }

            String name = value.getAsString().trim();
            if (!name.isEmpty()) {
                names.putIfAbsent(uuid, name);
            }
        }
    }

    private boolean writeFileCache(JsonObject serializedNames) {
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }

        File temporaryFile = new File(cacheFile.getPath() + ".tmp");
        JsonObject root = new JsonObject();
        root.add("names", serializedNames);
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
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void queue(final String uuid) {
        if (uuid.length() != 32 || names.containsKey(uuid)) {
            return;
        }

        Long nextAttempt = retryAfter.get(uuid);
        if (nextAttempt != null && System.currentTimeMillis() < nextAttempt.longValue()) {
            return;
        }
        if (!queued.add(uuid)) {
            return;
        }

        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        String name = readName(uuid);
                        if (name == null || name.trim().isEmpty()) {
                            scheduleRetry(uuid);
                        } else {
                            cacheName(uuid, name);
                            failureCounts.remove(uuid);
                            retryAfter.remove(uuid);
                        }
                    } catch (Throwable ignored) {
                        scheduleRetry(uuid);
                    } finally {
                        queued.remove(uuid);
                    }

                }
            });
        } catch (RuntimeException exception) {
            queued.remove(uuid);
        }
    }

    private void cacheName(String uuid, String name) {
        synchronized (cacheLock) {
            String previous = names.put(uuid, name);
            if (!name.equals(previous)) {
                cacheDirty = true;
            }
        }
    }

    private void scheduleRetry(String uuid) {
        Integer previousFailures = failureCounts.get(uuid);
        int failures = previousFailures == null ? 1 : Math.min(16, previousFailures.intValue() + 1);
        failureCounts.put(uuid, failures);

        int exponent = Math.min(6, failures - 1);
        long delay = Math.min(MAX_RETRY_DELAY_MS, INITIAL_RETRY_DELAY_MS << exponent);
        retryAfter.put(uuid, System.currentTimeMillis() + delay);
    }

    private String readName(String uuid) {
        String name = readProfileName(MOJANG_PROFILE_URL + uuid, "name");
        return name == null ? readProfileName(ASHCON_PROFILE_URL + uuid, "username") : name;
    }

    private String readProfileName(String url, String nameField) {
        HttpURLConnection connection = null;
        try {
            awaitProfileRequestSlot();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.addRequestProperty("User-Agent", "Schlangen-Skipper/1.0.0");
            connection.addRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }

            JsonObject root = readJsonObject(connection.getInputStream());
            if (!root.has(nameField) || root.get(nameField).isJsonNull()) {
                return null;
            }

            String name = root.get(nameField).getAsString();
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void awaitProfileRequestSlot() throws InterruptedException {
        synchronized (requestRateLock) {
            long waitMs = nextProfileRequestMs - System.currentTimeMillis();
            if (waitMs > 0L) {
                Thread.sleep(waitMs);
            }
            nextProfileRequestMs = System.currentTimeMillis() + PROFILE_REQUEST_INTERVAL_MS;
        }
    }

    private JsonObject readJsonObject(InputStream inputStream) throws IOException {
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
}
