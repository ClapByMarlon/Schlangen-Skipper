package de.laberlucas.botshopgui.util;

import de.laberlucas.botshopgui.util.BotRepository.BotInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class FavoriteBotsTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String UUID = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_UUID = "abcdef0123456789abcdef0123456789";
    private static final String API = "https://api.example.org/api/";

    @Test
    public void startsEmptyWithoutCreatingAFile() {
        File file = file();
        FavoriteBots favorites = new FavoriteBots(file);
        assertFalse(favorites.contains(UUID));
        assertFalse(favorites.isFavoritesOnly());
        assertFalse(favorites.hasReadFailed());
        assertFalse(file.exists());
    }

    @Test
    public void persistsFavoritesAndRemovesThemAcrossRestarts() throws Exception {
        File file = file();
        FavoriteBots favorites = new FavoriteBots(file);
        favorites.toggle(bot(UUID));
        FavoriteBots restarted = new FavoriteBots(file);
        assertTrue(restarted.contains(UUID));
        assertEquals(API, restarted.getBots(Collections.<BotInfo>emptyList()).get(0).getApiUrl());
        restarted.toggle(bot(UUID));
        assertFalse(new FavoriteBots(file).contains(UUID));
        assertEquals(0, temporary.getRoot().listFiles((dir, name) -> name.endsWith(".tmp")).length);
    }

    @Test
    public void normalizesUuidCaseAndDashes() throws Exception {
        FavoriteBots favorites = new FavoriteBots(file());
        favorites.toggle(bot("01234567-89AB-CDEF-0123-456789ABCDEF"));
        assertTrue(favorites.contains(UUID));
        favorites.toggle(bot(UUID));
        assertFalse(favorites.contains(UUID));
    }

    @Test
    public void keepsOtherFavoritesAndPreferredApiWhenSameBotHasSeveralSources() throws Exception {
        FavoriteBots favorites = new FavoriteBots(file());
        favorites.toggle(bot(UUID));
        favorites.toggle(bot(OTHER_UUID));
        BotInfo live = new BotInfo(UUID, API, "Renamed API");
        BotInfo anotherApi = new BotInfo(UUID, "https://other.example.org/api/", "Other API");
        assertEquals(2, favorites.getBots(Arrays.asList(live, anotherApi)).size());
        assertEquals("Renamed API", favorites.getBots(Arrays.asList(live, anotherApi)).get(0).getApiName());
        favorites.toggle(anotherApi);
        assertFalse(favorites.contains(UUID));
        assertTrue(favorites.contains(OTHER_UUID));
    }

    @Test
    public void retainsSavedBotsWhenCurrentApiListIsEmpty() throws Exception {
        File file = file();
        FavoriteBots favorites = new FavoriteBots(file);
        favorites.toggle(bot(UUID));
        FavoriteBots restarted = new FavoriteBots(file);
        assertEquals(Collections.singletonList(UUID), restarted.getIds());
        assertEquals(UUID, restarted.getBots(Collections.<BotInfo>emptyList()).get(0).getUuid());
    }

    @Test
    public void remembersFavoritesViewWithoutChangingTheSavedBots() throws Exception {
        File file = file();
        FavoriteBots favorites = new FavoriteBots(file);
        favorites.toggle(bot(UUID));
        favorites.setFavoritesOnly(true);
        FavoriteBots restarted = new FavoriteBots(file);
        assertTrue(restarted.isFavoritesOnly());
        assertTrue(restarted.contains(UUID));
        restarted.setFavoritesOnly(false);
        assertFalse(new FavoriteBots(file).isFavoritesOnly());
        assertTrue(new FavoriteBots(file).contains(UUID));
    }

    @Test
    public void failedSaveDoesNotMarkBotOrChangeView() throws Exception {
        File parentFile = temporary.newFile("blocked-parent");
        FavoriteBots favorites = new FavoriteBots(new File(parentFile, "favorites.json"));
        try { favorites.toggle(bot(UUID)); fail("Write should fail"); } catch (IOException expected) { }
        assertFalse(favorites.contains(UUID));
        try { favorites.setFavoritesOnly(true); fail("Write should fail"); } catch (IOException expected) { }
        assertFalse(favorites.isFavoritesOnly());
    }

    @Test
    public void corruptedFileIsPreservedInsteadOfOverwritten() throws Exception {
        File file = file();
        byte[] invalid = "{ broken json".getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), invalid);
        FavoriteBots favorites = new FavoriteBots(file);
        assertTrue(favorites.hasReadFailed());
        try { favorites.toggle(bot(UUID)); fail("Unreadable data must not be overwritten"); }
        catch (IOException expected) { }
        assertArrayEquals(invalid, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void rejectsMalformedUuidAndCredentialBearingApi() throws Exception {
        FavoriteBots favorites = new FavoriteBots(file());
        for (BotInfo invalid : Arrays.asList(bot("not-a-uuid"),
            new BotInfo(UUID, "https://user:secret@example.org/api/", "Invalid"))) {
            try { favorites.toggle(invalid); fail("Invalid bot should be rejected"); }
            catch (IOException expected) { }
        }
        assertTrue(favorites.getIds().isEmpty());
        assertFalse(file().exists());
    }

    @Test
    public void toleratesMissingApiDisplayName() throws Exception {
        File file = file();
        new FavoriteBots(file).toggle(new BotInfo(UUID, API, null));
        FavoriteBots restarted = new FavoriteBots(file);
        assertFalse(restarted.hasReadFailed());
        assertEquals(API, restarted.getBots(Collections.<BotInfo>emptyList()).get(0).getApiName());
    }

    private File file() { return new File(temporary.getRoot(), "favorites.json"); }
    private static BotInfo bot(String uuid) { return new BotInfo(uuid, API, "Example API"); }
}
