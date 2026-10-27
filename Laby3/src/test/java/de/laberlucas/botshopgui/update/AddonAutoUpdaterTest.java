package de.laberlucas.botshopgui.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AddonAutoUpdaterTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> platforms() {
        return Arrays.asList(new Object[][]{{AddonAutoUpdater.Platform.LABY3}, {AddonAutoUpdater.Platform.LABY4}});
    }

    private final AddonAutoUpdater.Platform platform;

    public AddonAutoUpdaterTest(AddonAutoUpdater.Platform platform) {
        this.platform = platform;
    }

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
        current = new File(directory, platform.assetName("1.0.2"));
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
        assertEquals(platform == AddonAutoUpdater.Platform.LABY3
            ? "old-other-addon.jar\n" + current.getName() + "\n" : "old-other-addon.jar", queued);
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
        assertNull(runUpdate());
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
    public void handlesPlatformSpecificRemovalQueuesSafely() throws Exception {
        assertTrue(queue.mkdir());
        if (platform == AddonAutoUpdater.Platform.LABY3) {
            expectFailure();
            assertFalse(target().exists());
        } else {
            assertEquals("1.0.3", runUpdate());
            assertTrue(target().isFile());
            assertTrue(queue.isDirectory());
        }
        assertTrue(current.isFile());
        assertNoTemporaryFiles();
    }

    @Test
    public void reusesAlreadyVerifiedDownloadWithoutSecondDownload() throws Exception {
        Files.write(target().toPath(), transport.jar);
        assertEquals("1.0.3", runUpdate());
        assertEquals(1, transport.calls);
        assertEquals(platform == AddonAutoUpdater.Platform.LABY3, queue.isFile());
    }

    @Test
    public void refusesToOverwriteAnExistingDifferentJar() throws Exception {
        Files.write(target().toPath(), new byte[]{5, 6});
        expectFailure();
        assertArrayEquals(new byte[]{5, 6}, Files.readAllBytes(target().toPath()));
        assertFalse(queue.exists());
    }

    @Test
    public void findsOwnReleaseBehindNewerForeignPlatformRelease() throws Exception {
        JsonArray releases = new JsonArray();
        JsonObject other = new com.google.gson.JsonParser().parse(release.toString()).getAsJsonObject();
        other.addProperty("tag_name", platform == AddonAutoUpdater.Platform.LABY3
            ? "laby4-v9.0.0" : "laby3-v9.0.0");
        releases.add(other);
        releases.add(release);
        assertEquals("1.0.3", runUpdate(releases));
        assertEquals(2, transport.calls);
    }

    @Test
    public void skipsReleaseWithOnlyOtherPlatformAsset() throws Exception {
        asset().addProperty("name", (platform == AddonAutoUpdater.Platform.LABY3
            ? AddonAutoUpdater.Platform.LABY4 : AddonAutoUpdater.Platform.LABY3).assetName("1.0.3"));
        assertNull(runUpdate());
        assertEquals(1, transport.calls);
        assertFalse(target().exists());
    }

    @Test
    public void rejectsOtherPlatformMetadataDespiteCorrectFilenameAndHash() throws Exception {
        byte[] correctJar = transport.jar;
        AddonAutoUpdaterTest other = new AddonAutoUpdaterTest(platform == AddonAutoUpdater.Platform.LABY3
            ? AddonAutoUpdater.Platform.LABY4 : AddonAutoUpdater.Platform.LABY3);
        transport.jar = other.jar("1.0.3", "a8d3fbba-09e5-42cc-9e75-1875f404157a");
        File fixture = temporary.newFile();
        Files.write(fixture.toPath(), transport.jar);
        asset().addProperty("size", transport.jar.length);
        asset().addProperty("digest", "sha256:" + AddonAutoUpdater.sha256(fixture.toPath()));
        expectFailure();
        assertTrue(current.isFile());
        assertFalse(target().exists());
        assertFalse(queue.exists());
        transport.jar = correctJar;
    }

    @Test
    public void acceptsPlatformSpecificReleaseTags() throws Exception {
        String tag = (platform == AddonAutoUpdater.Platform.LABY3 ? "laby3" : "laby4") + "-v1.0.3";
        release.addProperty("tag_name", tag);
        asset().addProperty("browser_download_url",
            "https://github.com/ClapByMarlon/botshop-gui/releases/download/" + tag + "/" + target().getName());
        assertEquals("1.0.3", runUpdate());
    }

    @Test
    public void selectsHighestOwnVersionRegardlessOfReleaseOrder() throws Exception {
        JsonObject older = new com.google.gson.JsonParser().parse(release.toString()).getAsJsonObject();
        configureRelease("1.0.4", "a8d3fbba-09e5-42cc-9e75-1875f404157a");
        JsonArray releases = new JsonArray();
        releases.add(older);
        releases.add(release);
        assertEquals("1.0.4", runUpdate(releases));
        assertTrue(new File(directory, platform.assetName("1.0.4")).isFile());
        assertFalse(target().exists());
        assertEquals(2, transport.calls);
    }

    @Test
    public void rejectsDuplicateMatchingAssets() throws Exception {
        release.getAsJsonArray("assets").add(asset());
        expectFailure();
        assertEquals(1, transport.calls);
        assertFalse(target().exists());
    }

    private String runUpdate() throws Exception {
        JsonArray releases = new JsonArray();
        releases.add(release);
        return runUpdate(releases);
    }

    private String runUpdate(JsonArray releases) throws Exception {
        transport.release = releases.toString().getBytes(StandardCharsets.UTF_8);
        return new AddonAutoUpdater(platform, transport).checkAndInstall(current, queue);
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
        return new File(directory, platform.assetName("1.0.3"));
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
        asset.addProperty("name", platform.assetName(version));
        asset.addProperty("browser_download_url", downloadUrl(version));
        asset.addProperty("size", transport.jar.length);
        asset.addProperty("digest", "sha256:" + AddonAutoUpdater.sha256(fixture.toPath()));
        JsonArray assets = new JsonArray();
        assets.add(asset);
        release.add("assets", assets);
    }

    private String downloadUrl(String version) {
        return "https://github.com/ClapByMarlon/botshop-gui/releases/download/v" + version
            + "/" + platform.assetName(version);
    }

    private byte[] jar(String version, String uuid) throws IOException {
        JsonObject addon = new JsonObject();
        if (platform == AddonAutoUpdater.Platform.LABY3) {
            addon.addProperty("uuid", uuid);
            addon.addProperty("addonVersion", version);
        } else {
            addon.addProperty("namespace", uuid.equals("a8d3fbba-09e5-42cc-9e75-1875f404157a")
                ? "schlangen_skipper" : "wrong_addon");
            addon.addProperty("version", version);
            JsonObject build = new JsonObject();
            build.addProperty("namedMappings", true);
            addon.add("build", build);
        }
        addon.addProperty("mainClass", "de.laberlucas.botshopgui.BotShopAddon");
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
            if (url.toString().equals(AddonAutoUpdater.RELEASES_URL)) {
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
