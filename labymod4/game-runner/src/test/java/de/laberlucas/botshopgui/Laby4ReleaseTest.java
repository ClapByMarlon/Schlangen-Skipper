package de.laberlucas.botshopgui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class Laby4ReleaseTest {

    @Test
    public void releaseContainsLaby4EntrypointForMinecraft189() throws Exception {
        try (JarFile jar = release()) {
            JsonObject metadata = json(jar, "addon.json");
            assertEquals("schlangen_skipper", metadata.get("namespace").getAsString());
            assertEquals("ClapByMarlon", metadata.get("author").getAsString());
            assertEquals("1.8.9", metadata.get("compatibleMinecraftVersions").getAsString());
            String mainClass = metadata.get("mainClass").getAsString();
            assertNotNull(jar.getJarEntry(mainClass.replace('.', '/') + ".class"));
            assertTrue(metadata.getAsJsonObject("build").get("namedMappings").getAsBoolean());
        }
    }

    @Test
    public void releaseProvidesTranslationsForNativeSettings() throws Exception {
        try (JarFile jar = release()) {
            for (String language : new String[]{"de_de", "en_us"}) {
                JsonObject settings = json(jar, "assets/schlangen_skipper/i18n/" + language + ".json")
                    .getAsJsonObject("schlangen_skipper").getAsJsonObject("settings");
                assertTrue(settings.has("enabled"));
                assertTrue(settings.has("openKey"));
            }
        }
    }

    @Test
    public void releaseExcludesLaby3UpdaterAndForgeDependencies() throws Exception {
        try (JarFile jar = release()) {
            assertNull(jar.getJarEntry("de/laberlucas/botshopgui/update/AddonAutoUpdater.class"));
            assertNull(jar.getJarEntry("de/laberlucas/botshopgui/feature/ClientTickHandler.class"));
            assertNull(jar.getJarEntry("mcmod.info"));
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    String bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                    assertFalse(entry.getName(), bytecode.contains("net/minecraftforge/"));
                    assertFalse(entry.getName(), bytecode.contains("net/labymod/api/LabyModAddon"));
                }
            }
        }
    }

    private static JarFile release() throws Exception {
        return new JarFile(System.getProperty("releaseJar"));
    }

    private static JsonObject json(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(path, entry);
        try (InputStream input = jar.getInputStream(entry)) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }
}
