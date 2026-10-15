package de.laberlucas.botshopgui.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class AddonAutoUpdaterTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private File directory;
    private File current;
    private File queue;
    private MockTransport transport;
    private JsonObject release;

    @Before
    public void setup() throws Exception {
        directory = temporary.newFolder("addons-1.8");
        current = new File(directory, "schlangen-skipper-1.0.2.jar");
        Files.write(current.toPath(), jar("1.0.2", "a8d3fbba-09e5-42cc-9e75-1875f404157a"));
        queue = new File(directory, ".delete");
        transport = new MockTransport();
        configureRelease("1.0.3", "a8d3fbba-09e5-42cc-9e75-1875f404157a");
    }

    @Test
    public void installsVerifiedUpdatePreservingRunningJarOtherAddonsAndQueue() throws Exception {
        byte[] original = Files.readAllBytes(current.toPath());
        File unrelated = new File(directory, "another-addon.jar");
        Files.write(unrelated.toPath(), new byte[]{1, 2, 3});
        Files.write(queue.toPath(), "old-other-addon.jar".getBytes(StandardCharsets.UTF_8));

        assertEquals("1.0.3", runUpdate());

        assertArrayEquals(original, Files.readAllBytes(current.toPath()));
        assertArrayEquals(transport.jar, Files.readAllBytes(target().toPath()));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(unrelated.toPath()));
        String queued = new String(Files.readAllBytes(queue.toPath()), StandardCharsets.UTF_8);
        assertEquals("old-other-addon.jar\n" + current.getName() + "\n", queued);
        assertEquals(2, transport.calls);
        assertNoTemporaryFiles();
    }

    @Test
    public void skipsCurrentAndOlderVersionsWithoutDownloading() throws Exception {
        for (String version : new String[]{"1.0.2", "1.0.1"}) {
            configureRelease(version, "a8d3fbba-09e5-42cc-9e75-1875f404157a");
            transport.calls = 0;
            assertNull(runUpdate());
            assertEquals(1, transport.calls);
        }
        assertFalse(queue.exists());
    }

    @Test
    public void skipsPrereleasesAndDrafts() throws Exception {
        release.addProperty("prerelease", true);
        assertNull(runUpdate());
        release.addProperty("prerelease", false);
        release.addProperty("draft", true);
        assertNull(runUpdate());
        assertEquals(2, transport.calls);
        assertFalse(target().exists());
    }

    @Test
    public void comparesVersionsNumericallyWithoutDowngrading() {
        assertTrue(AddonAutoUpdater.compareVersions("1.10.0", "1.9.99") > 0);
        assertEquals(0, AddonAutoUpdater.compareVersions("1.2", "1.2.0"));
        assertTrue(AddonAutoUpdater.compareVersions("2.0.0", "10.0.0") < 0);
    }

    @Test
    public void rejectsTraversalAndForeignDownloadUrls() throws Exception {
        release.addProperty("tag_name", "v../../other");
        expectFailure();
        release.addProperty("tag_name", "v1.0.3");
        asset().addProperty("browser_download_url", "https://example.com/addon.jar");
        expectFailure();
        assertEquals(2, transport.calls);
        assertFalse(queue.exists());
    }

    @Test
    public void rejectsMissingOrWrongChecksumAndCleansTemporaryDownload() throws Exception {
        asset().addProperty("digest", "sha256:" + new String(new char[64]).replace('\0', '0'));
        expectFailure();
        assertFalse(target().exists());
        assertFalse(queue.exists());
        assertNoTemporaryFiles();
        asset().remove("digest");
        expectFailure();
    }

    @Test
    public void rejectsForeignAddonEvenWithCorrectChecksum() throws Exception {
        configureRelease("1.0.3", "00000000-0000-0000-0000-000000000000");
        expectFailure();
        assertTrue(current.isFile());
        assertFalse(target().exists());
        assertFalse(queue.exists());
    }

    @Test
    public void rejectsMismatchedVersionInJar() throws Exception {
        configureRelease("1.0.4", "a8d3fbba-09e5-42cc-9e75-1875f404157a");
        release.addProperty("tag_name", "v1.0.3");
        asset().addProperty("name", target().getName());
        asset().addProperty("browser_download_url", downloadUrl("1.0.3"));
        expectFailure();
        assertFalse(target().exists());
        assertFalse(queue.exists());
    }

    @Test
    public void interruptedDownloadKeepsOldJarAndDoesNotQueueDeletion() throws Exception {
        transport.interruptDownload = true;
        expectFailure();
        assertTrue(current.isFile());
        assertFalse(target().exists());
        assertFalse(queue.exists());
        assertNoTemporaryFiles();
    }

    @Test
    public void failedQueueWriteRollsBackNewJar() throws Exception {
        assertTrue(queue.mkdir());
        expectFailure();
        assertTrue(current.isFile());
        assertFalse(target().exists());
        assertNoTemporaryFiles();
    }

    @Test
    public void reusesAlreadyVerifiedDownloadWithoutSecondDownload() throws Exception {
        Files.write(target().toPath(), transport.jar);
        assertEquals("1.0.3", runUpdate());
        assertEquals(1, transport.calls);
        assertTrue(queue.isFile());
    }

    @Test
    public void refusesToOverwriteAnExistingDifferentJar() throws Exception {
        Files.write(target().toPath(), new byte[]{5, 6});
        expectFailure();
        assertArrayEquals(new byte[]{5, 6}, Files.readAllBytes(target().toPath()));
        assertFalse(queue.exists());
    }

    private String runUpdate() throws Exception {
        transport.release = release.toString().getBytes(StandardCharsets.UTF_8);
        return new AddonAutoUpdater(transport).checkAndInstall(current, queue);
    }

    private void expectFailure() throws Exception {
        try {
            runUpdate();
            fail("Invalid update must fail");
        } catch (IOException | IllegalArgumentException | NullPointerException expected) {
            // Invalid metadata and failed I/O must not replace the running addon.
        }
    }

    private File target() {
        return new File(directory, "schlangen-skipper-1.0.3.jar");
    }

    private void assertNoTemporaryFiles() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".tmp"));
        assertNotNull(files);
        assertEquals(0, files.length);
    }

    private JsonObject asset() {
        return release.getAsJsonArray("assets").get(0).getAsJsonObject();
    }

    private void configureRelease(String version, String uuid) throws Exception {
        transport.jar = jar(version, uuid);
        File fixture = temporary.newFile();
        Files.write(fixture.toPath(), transport.jar);
        release = new JsonObject();
        release.addProperty("tag_name", "v" + version);
        release.addProperty("draft", false);
        release.addProperty("prerelease", false);
        JsonObject asset = new JsonObject();
        asset.addProperty("name", "schlangen-skipper-" + version + ".jar");
        asset.addProperty("browser_download_url", downloadUrl(version));
        asset.addProperty("size", transport.jar.length);
        asset.addProperty("digest", "sha256:" + AddonAutoUpdater.sha256(fixture.toPath()));
        JsonArray assets = new JsonArray();
        assets.add(asset);
        release.add("assets", assets);
    }

    private static String downloadUrl(String version) {
        return "https://github.com/ClapByMarlon/botshop-gui/releases/download/v" + version
            + "/schlangen-skipper-" + version + ".jar";
    }

    private static byte[] jar(String version, String uuid) throws IOException {
        JsonObject addon = new JsonObject();
        addon.addProperty("uuid", uuid);
        addon.addProperty("mainClass", "de.laberlucas.botshopgui.BotShopAddon");
        addon.addProperty("addonVersion", version);
        addon.addProperty("compatibleMinecraftVersions", "1.8.9");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            output.putNextEntry(new JarEntry("addon.json"));
            output.write(addon.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("de/laberlucas/botshopgui/BotShopAddon.class"));
            output.write(new byte[]{0, 1, 2});
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static final class MockTransport implements AddonAutoUpdater.Transport {
        byte[] release;
        byte[] jar;
        int calls;
        boolean interruptDownload;

        @Override
        public InputStream open(URL url) {
            calls++;
            if (url.toString().equals(AddonAutoUpdater.LATEST_RELEASE_URL)) {
                return new ByteArrayInputStream(release);
            }
            if (!interruptDownload) {
                return new ByteArrayInputStream(jar);
            }
            return new FilterInputStream(new ByteArrayInputStream(jar)) {
                boolean started;

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    if (started) {
                        throw new IOException("Simulated network failure");
                    }
                    started = true;
                    return super.read(buffer, offset, Math.min(length, 10));
                }
            };
        }
    }
}
