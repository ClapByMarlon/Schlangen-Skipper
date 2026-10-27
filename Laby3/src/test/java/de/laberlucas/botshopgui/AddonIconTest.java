package de.laberlucas.botshopgui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class AddonIconTest {

    @Test
    public void metadataUsesGithubIconAndKeepsUpdaterMetadataSmall() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/addon.json")) {
            assertNotNull(stream);
            JsonObject metadata = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            assertEquals("https://raw.githubusercontent.com/ClapByMarlon/Schlangen-Skipper/main/"
                + "Laby3/src/main/resources/assets/schlangen_skipper/textures/icon.png",
                metadata.get("icon").getAsString());
            assertTrue(metadata.toString().getBytes(StandardCharsets.UTF_8).length < 64 * 1024);
        }
    }

    @Test
    public void bundledIconIsSquarePngWithTransparentCorners() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
            "/assets/schlangen_skipper/textures/icon.png")) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(image.getWidth(), image.getHeight());
            assertTrue(image.getWidth() >= 64);
            assertTrue(image.getColorModel().hasAlpha());
            int last = image.getWidth() - 1;
            for (int[] point : new int[][]{{0, 0}, {last, 0}, {0, last}, {last, last}}) {
                assertEquals(0, image.getRGB(point[0], point[1]) >>> 24);
            }
            assertTrue((image.getRGB(last / 2, last / 2) >>> 24) > 0);
        }
    }
}
