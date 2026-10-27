package de.laberlucas.botshopgui.update;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class Laby4UpdaterIntegrationTest {

    @Test
    public void acceptsActualSdkReleaseMetadataWithoutTouchingInstalledJar() throws Exception {
        File jar = new File(System.getProperty("releaseJar"));
        long modified = jar.lastModified();
        AtomicInteger calls = new AtomicInteger();
        AddonAutoUpdater updater = new AddonAutoUpdater(AddonAutoUpdater.Platform.LABY4, url -> {
            assertEquals(AddonAutoUpdater.RELEASES_URL, url.toString());
            calls.incrementAndGet();
            return new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8));
        });
        assertNull(updater.checkAndInstall(jar, null));
        assertEquals(1, calls.get());
        assertEquals(modified, jar.lastModified());
    }
}
