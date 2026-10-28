package de.laberlucas.botshopgui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.io.File;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class BrowserFavoritesIntegrationTest {
    private static final String GUI = "de/laberlucas/botshopgui/gui/";
    private static BufferedImage buttonPixels;

    @Test
    public void neutralStarsHaveNoPinkStripesEvenOnHover() throws Exception {
        Method painter = buttonPainter();
        for (boolean hovered : new boolean[] {false, true}) {
            buttonPixels = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            painter.invoke(null, 4, 4, 23, 23, 5, hovered ? 0xFF202A39 : 0xFF171E29, hovered);
            for (int y = 0; y < buttonPixels.getHeight(); y++) {
                for (int x = 0; x < buttonPixels.getWidth(); x++) {
                    int pixel = buttonPixels.getRGB(x, y);
                    if ((pixel >>> 24) == 0) continue;
                    assertTrue("Neutral frame must not contain pink at " + x + "," + y,
                        ((pixel >> 16) & 255) <= (pixel & 255));
                }
            }
            assertEquals(hovered ? 0xFF202A39 : 0xFF171E29, buttonPixels.getRGB(13, 13));
        }
    }

    @Test
    public void pinkButtonsKeepConsistentInsetEdgesAndProducePreview() throws Exception {
        Method painter = buttonPainter();
        buttonPixels = new BufferedImage(220, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D background = buttonPixels.createGraphics();
        background.setColor(new Color(0x141B26));
        background.fillRect(0, 0, 220, 64);
        background.dispose();
        for (int state = 0; state < 2; state++) {
            int x = 4 + state * 110;
            int fill = state == 0 ? 0xFF6E294E : 0xFF84365C;
            painter.invoke(null, x, 4, x + 100, 34, 5, fill, state == 1);
            assertEquals(fill, buttonPixels.getRGB(x + 50, 18));
            assertEquals(0xFF0A0E15, buttonPixels.getRGB(x + 50, 30));
            assertEquals(0xFF05070B, buttonPixels.getRGB(x + 50, 33));
            painter.invoke(null, x, 40, x + 19, 59, 5,
                state == 0 ? 0xFF171E29 : 0xFF202A39, state == 1);
        }
        BufferedImage scaled = new BufferedImage(880, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(buttonPixels, 0, 0, 880, 256, null);
        graphics.dispose();
        ImageIO.write(scaled, "png", new File("build/favorites-buttons-preview.png"));
    }

    // Execute the packaged renderer with a raster drawRect sink, without starting Minecraft/OpenGL.
    private static Method buttonPainter() throws Exception {
        String owner = "de/laberlucas/botshopgui/ButtonRaster";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        try (JarFile jar = release()) {
            ClassNode gui = read(jar, GUI + "BotSelectionGui");
            for (MethodNode method : gui.methods) {
                if (!method.name.equals("drawButtonFrame") && !method.name.equals("drawRoundedRect")) continue;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.desc.equals("(IIIII)V")) {
                        call.owner = BrowserFavoritesIntegrationTest.class.getName().replace('.', '/');
                        call.name = "rasterRect";
                    } else if (call.name.equals("drawRoundedRect")) {
                        call.owner = owner;
                    }
                }
                method.accept(writer);
            }
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> raster = new ClassLoader(BrowserFavoritesIntegrationTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Method painter = raster.getDeclaredMethod("drawButtonFrame", int.class, int.class, int.class,
            int.class, int.class, int.class, boolean.class);
        painter.setAccessible(true);
        return painter;
    }

    public static void rasterRect(int left, int top, int right, int bottom, int color) {
        Graphics2D graphics = buttonPixels.createGraphics();
        graphics.setColor(new Color(color, true));
        graphics.fillRect(left, top, right - left, bottom - top);
        graphics.dispose();
    }

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
