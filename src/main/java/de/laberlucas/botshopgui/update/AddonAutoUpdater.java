package de.laberlucas.botshopgui.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AddonAutoUpdater {

    public static final String LATEST_RELEASE_URL =
        "https://api.github.com/repos/ClapByMarlon/botshop-gui/releases/latest";
    private static final String DOWNLOAD_BASE =
        "https://github.com/ClapByMarlon/botshop-gui/releases/download/";
    private static final String ADDON_UUID = "a8d3fbba-09e5-42cc-9e75-1875f404157a";
    private static final String MAIN_CLASS = "de.laberlucas.botshopgui.BotShopAddon";
    private static final long MAX_JAR_BYTES = 20L * 1024 * 1024;

    private final Transport transport;

    public AddonAutoUpdater() {
        this(new HttpTransport());
    }

    AddonAutoUpdater(Transport transport) {
        this.transport = transport;
    }

    public String checkAndInstall(File currentJar, File deleteQueue)
        throws Exception {
        if (currentJar == null || !currentJar.isFile()) {
            return null;
        }
        Path current = currentJar.toPath().toRealPath();
        Path directory = current.getParent();
        Path queue = deleteQueue.toPath().toAbsolutePath().normalize();
        if (!queue.getParent().toRealPath().equals(directory)) {
            throw new IOException("Update queue is outside the addon directory");
        }
        if (Files.isSymbolicLink(queue)) {
            throw new IOException("Update queue must not be a symbolic link");
        }
        String currentVersion = validateJar(current, null);

        JsonObject release;
        try (InputStream input = transport.open(new URL(LATEST_RELEASE_URL))) {
            release = json(readLimited(input, 1024 * 1024));
        }
        if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
            return null;
        }
        String tag = release.get("tag_name").getAsString();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        if (compareVersions(version, currentVersion) <= 0) {
            return null;
        }

        String name = "schlangen-skipper-" + version + ".jar";
        JsonObject asset = null;
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject candidate = element.getAsJsonObject();
            if (name.equals(candidate.get("name").getAsString())) {
                if (asset != null) {
                    throw new IOException("Ambiguous release assets");
                }
                asset = candidate;
            }
        }
        if (asset == null) {
            throw new IOException("Release has no matching addon JAR");
        }
        String download = asset.get("browser_download_url").getAsString();
        String digest = asset.get("digest").getAsString();
        long size = asset.get("size").getAsLong();
        if (!download.equals(DOWNLOAD_BASE + tag + "/" + name)
            || !digest.matches("sha256:[0-9a-fA-F]{64}") || size <= 0 || size > MAX_JAR_BYTES) {
            throw new IOException("Invalid release asset metadata");
        }
        String expectedHash = digest.substring(7);
        Path target = directory.resolve(name);
        boolean created = false;
        Path temporary = null;
        try {
            if (Files.exists(target)) {
                if (!target.toRealPath().getParent().equals(directory)) {
                    throw new IOException("Update target is outside the addon directory");
                }
                verify(target, version, size, expectedHash);
            } else {
                temporary = Files.createTempFile(directory, ".schlangen-skipper-", ".tmp");
                try (InputStream input = transport.open(new URL(download));
                     OutputStream output = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    long count = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        count += read;
                        if (count > size || count > MAX_JAR_BYTES) {
                            throw new IOException("Update exceeds announced size");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                verify(temporary, version, size, expectedHash);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target);
                }
                created = true;
            }

            // LabyMod processes this queue before discovering addon JARs on next launch.
            queueDeletion(queue, current.getFileName().toString());
            return version;
        } catch (Exception exception) {
            if (created && !(exception instanceof QueueRollbackException)) {
                Files.deleteIfExists(target);
            }
            throw exception;
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    static int compareVersions(String left, String right) {
        if (!left.matches("[0-9]+(?:\\.[0-9]+){1,3}")
            || !right.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new IllegalArgumentException("Unsupported stable version");
        }
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            BigInteger x = index < a.length ? new BigInteger(a[index]) : BigInteger.ZERO;
            BigInteger y = index < b.length ? new BigInteger(b[index]) : BigInteger.ZERO;
            int comparison = x.compareTo(y);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static void verify(Path path, String version, long size, String expectedHash)
        throws Exception {
        if (Files.size(path) != size || !sha256(path).equalsIgnoreCase(expectedHash)) {
            throw new IOException("Update checksum or size mismatch");
        }
        validateJar(path, version);
    }

    static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String validateJar(Path path, String version) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry metadata = jar.getJarEntry("addon.json");
            if (metadata == null || jar.getJarEntry(MAIN_CLASS.replace('.', '/') + ".class") == null) {
                throw new IOException("Not a Schlangen Skipper addon JAR");
            }
            JsonObject addon;
            try (InputStream input = jar.getInputStream(metadata)) {
                addon = json(readLimited(input, 64 * 1024));
            }
            if (!ADDON_UUID.equals(addon.get("uuid").getAsString())
                || !MAIN_CLASS.equals(addon.get("mainClass").getAsString())
                || (version != null && !version.equals(addon.get("addonVersion").getAsString()))
                || !"1.8.9".equals(addon.get("compatibleMinecraftVersions").getAsString())) {
                throw new IOException("Update addon identity, version or Minecraft version mismatch");
            }
            return addon.get("addonVersion").getAsString();
        }
    }

    private static void queueDeletion(Path queue, String filename) throws IOException {
        if (filename.contains("\n") || filename.contains("\r")) {
            throw new IOException("Invalid addon filename");
        }
        try (FileChannel channel = FileChannel.open(queue, StandardOpenOption.CREATE,
                 StandardOpenOption.WRITE, StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            long originalSize = channel.size();
            ByteBuffer data = StandardCharsets.UTF_8.encode("\n" + filename + "\n");
            try {
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            } catch (IOException exception) {
                try {
                    channel.truncate(originalSize);
                    channel.force(true);
                } catch (IOException rollbackFailure) {
                    // Keep the verified new JAR if the old one may already be queued.
                    throw new QueueRollbackException();
                }
                throw exception;
            }
        }
    }

    private static final class QueueRollbackException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static JsonObject json(byte[] bytes) {
        return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (result.size() + read > maximum) {
                throw new IOException("Response too large");
            }
            result.write(buffer, 0, read);
        }
        return result.toByteArray();
    }

    interface Transport {
        InputStream open(URL url) throws IOException;
    }

    private static final class HttpTransport implements Transport {
        @Override
        public InputStream open(URL url) throws IOException {
            for (int redirects = 0; redirects < 6; redirects++) {
                String host = url.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null
                    || !("api.github.com".equals(host) || "github.com".equals(host)
                    || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host))) {
                    throw new IOException("Untrusted update URL");
                }
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "Schlangen-Skipper-Updater");
                connection.setRequestProperty("Accept", "api.github.com".equals(host)
                    ? "application/vnd.github+json" : "application/octet-stream");
                try {
                    int status = connection.getResponseCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) {
                            throw new IOException("Update redirect has no destination");
                        }
                        url = new URL(url, location);
                        connection.disconnect();
                        continue;
                    }
                    if (status != 200) {
                        throw new IOException("Update server returned HTTP " + status);
                    }
                    return new FilterInputStream(connection.getInputStream()) {
                        @Override
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                connection.disconnect();
                            }
                        }
                    };
                } catch (IOException exception) {
                    connection.disconnect();
                    throw exception;
                }
            }
            throw new IOException("Too many update redirects");
        }
    }
}
