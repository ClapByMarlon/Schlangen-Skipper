package de.laberlucas.botshopgui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class BrowserIntegrationTest {

    private static final String MIXINS = "de/laberlucas/botshopgui/v1_8_9/mixins/";
    private static final String BROWSER = "de.laberlucas.botshopgui.gui.Laby4BotSelectionGui";

    @Test
    public void releaseRegistersHudMixinUnderAddonNamespace() throws Exception {
        try (JarFile jar = release()) {
            var entry = jar.getJarEntry("1.8.9-schlangen_skipper.mixins.json");
            assertNotNull("SDK mixin config must be discoverable by the addon loader", entry);
            JsonObject config;
            try (InputStream input = jar.getInputStream(entry)) {
                config = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            }
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(MIXINS.substring(0, MIXINS.length() - 1).replace('/', '.'),
                config.get("package").getAsString());
            assertEquals(1, config.getAsJsonArray("client").size());
            for (var mixin : config.getAsJsonArray("client")) {
                assertNotNull(jar.getJarEntry(MIXINS + mixin.getAsString() + ".class"));
            }
        }
    }

    @Test
    public void hooksMatchSdkTargetsAndCancelOnlyBrowserRenderPasses() throws Exception {
        assertTargetMethod("net/minecraft/client/gui/GuiIngame", "renderGameOverlay", "(F)V");
        try (JarFile jar = release()) {
            assertHook(readClass(jar, MIXINS + "MixinGuiIngame.class"), List.of("renderGameOverlay"));
            assertNull(jar.getJarEntry(MIXINS + "MixinIngameActivityOverlay.class"));
        }
    }

    @Test
    public void searchKeepsCustomRenderingAndCursorUpdates() throws Exception {
        try (JarFile jar = release()) {
            ClassNode main = readClass(jar, "de/laberlucas/botshopgui/BotShopAddon.class");
            assertTrue(hasCall(main, "net/labymod/api/client/gui/screen/widget/converter/WidgetConverterRegistry",
                "exclude"));
            assertTrue(hasCall(main, "de/laberlucas/botshopgui/gui/ScopedVisibility", "hide"));
            assertTrue(hasCall(main, "de/laberlucas/botshopgui/gui/ScopedVisibility", "restore"));
            ClassNode browser = readClass(jar, BROWSER.replace('.', '/') + ".class");
            assertTrue(hasCall(browser, "de/laberlucas/botshopgui/BotShopAddon", "restoreBrowserOverlays"));
            ClassNode field = readClass(jar, "de/laberlucas/botshopgui/gui/BrowserSearchField.class");
            assertEquals("net/minecraft/client/gui/GuiTextField", field.superName);
            assertTrue(hasCall(field, "net/minecraft/client/gui/GuiTextField", "drawTextBox"));
            ClassNode gui = readClass(jar, "de/laberlucas/botshopgui/gui/BotSelectionGui.class");
            assertTrue(hasCall(gui, "net/minecraft/client/gui/GuiTextField", "updateCursorCounter"));
            assertTrue(hasCall(gui, "net/minecraft/client/gui/GuiTextField", "getText"));
            assertTrue(hasCall(gui, "net/minecraft/client/gui/GuiTextField", "setText"));
        }
    }

    private static void assertHook(ClassNode mixin, List<String> targets) {
        for (MethodNode method : mixin.methods) {
            if (method.visibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                    continue;
                }
                assertEquals(targets, value(annotation, "method"));
                assertEquals(Boolean.TRUE, value(annotation, "cancellable"));
                @SuppressWarnings("unchecked")
                List<AnnotationNode> at = (List<AnnotationNode>) value(annotation, "at");
                assertEquals("INVOKE", value(at.get(0), "value"));
                assertEquals("Lnet/minecraft/client/renderer/EntityRenderer;setupOverlayRendering()V",
                    value(at.get(0), "target"));
                assertArrayEquals(new String[]{"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"},
                    (String[]) value(at.get(0), "shift"));
                boolean scoped = false;
                boolean cancels = false;
                for (var instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode constant && BROWSER.equals(constant.cst)) {
                        scoped = true;
                    }
                    if (instruction instanceof MethodInsnNode call && call.name.equals("cancel")) {
                        cancels = true;
                    }
                }
                assertTrue("hook must identify only our browser", scoped);
                assertTrue("hook must cancel rendering", cancels);
                return;
            }
        }
        fail("Missing cancellable render hook in " + mixin.name);
    }

    private static Object value(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) {
            if (node.values.get(i).equals(key)) {
                return node.values.get(i + 1);
            }
        }
        return null;
    }

    private static void assertTargetMethod(String owner, String name, String descriptor) throws Exception {
        for (String path : System.getProperty("minecraftClasspath").split(File.pathSeparator)) {
            if (!path.endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(path)) {
                if (jar.getJarEntry(owner + ".class") == null) {
                    continue;
                }
                for (MethodNode method : readClass(jar, owner + ".class").methods) {
                    if (method.name.equals(name) && method.desc.equals(descriptor)) {
                        return;
                    }
                }
                fail("Missing target " + owner + "." + name + descriptor);
            }
        }
        fail("Missing SDK class " + owner);
    }

    private static boolean hasCall(ClassNode node, String owner, String name) {
        for (MethodNode method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ClassNode readClass(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(path, entry);
        try (InputStream input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static JarFile release() throws Exception {
        return new JarFile(System.getProperty("releaseJar"));
    }
}
