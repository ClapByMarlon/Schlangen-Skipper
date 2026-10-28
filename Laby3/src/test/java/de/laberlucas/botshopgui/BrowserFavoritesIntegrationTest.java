package de.laberlucas.botshopgui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class BrowserFavoritesIntegrationTest {
    private static final String GUI = "de/laberlucas/botshopgui/gui/";

    @Test
    public void laby3EntrypointOpensFavoriteCapableBrowser() throws Exception {
        try (JarFile jar = release()) {
            ClassNode main = read(jar, "de/laberlucas/botshopgui/BotShopAddon");
            boolean opens = false;
            for (MethodNode method : main.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode && instruction.getOpcode() == Opcodes.NEW
                        && ((TypeInsnNode) instruction).desc.equals(GUI + "Laby3BotSelectionGui")) opens = true;
                }
            }
            assertTrue(opens);
            assertNotNull(jar.getJarEntry("de/laberlucas/botshopgui/util/FavoriteBots.class"));
            ClassNode gui = read(jar, GUI + "Laby3BotSelectionGui");
            assertTrue(hasCall(gui, "toggle"));
            assertTrue(hasCall(gui, "setFavoritesOnly"));
            assertTrue(hasCall(gui, "getBots"));
        }
    }

    @Test
    public void starClickIsConsumedBeforeAnyShopCanOpen() throws Exception {
        try (JarFile jar = release()) {
            ClassNode gui = read(jar, GUI + "BotSelectionGui");
            boolean guarded = false;
            for (MethodNode method : gui.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode)
                        || !((MethodInsnNode) instruction).name.equals("handleFavoriteClick")) continue;
                    AbstractInsnNode branch = nextInstruction(instruction);
                    assertEquals(Opcodes.IFEQ, branch.getOpcode());
                    assertEquals(Opcodes.RETURN, nextInstruction(branch).getOpcode());
                    guarded = true;
                }
                if (method.name.equals("handleFavoriteClick")) {
                    assertTrue(hasCall(method, "toggleFavorite"));
                    assertTrue(hasCall(method, "refreshFilteredBots"));
                    assertFalse(hasCall(method, "openBotshop"));
                }
            }
            assertTrue("A star click must not fall through to opening the shop", guarded);
        }
    }

    @Test
    public void enteringFavoritesClearsOldFiltersAndPersistsTheView() throws Exception {
        try (JarFile jar = release()) {
            ClassNode gui = read(jar, GUI + "BotSelectionGui");
            MethodNode handler = null;
            for (MethodNode method : gui.methods) if (method.name.equals("handleButton")) handler = method;
            assertNotNull(handler);
            assertTrue(hasCall(handler, "saveFavoritesView"));
            boolean clearsApi = false;
            boolean clearsSearch = false;
            for (AbstractInsnNode instruction : handler.instructions) {
                if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.PUTFIELD
                    && ((FieldInsnNode) instruction).name.equals("selectedApiUrl")) clearsApi = true;
                if (instruction instanceof MethodInsnNode
                    && ((MethodInsnNode) instruction).desc.equals("(Ljava/lang/String;)V")) clearsSearch = true;
            }
            assertTrue(clearsApi);
            assertTrue(clearsSearch);
        }
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static boolean hasCall(ClassNode node, String name) {
        for (MethodNode method : node.methods) if (hasCall(method, name)) return true;
        return false;
    }

    private static boolean hasCall(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)) return true;
        }
        return false;
    }

    private static JarFile release() throws Exception { return new JarFile(System.getProperty("releaseJar")); }
    private static ClassNode read(JarFile jar, String name) throws Exception {
        assertNotNull(name, jar.getJarEntry(name + ".class"));
        try (InputStream input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
