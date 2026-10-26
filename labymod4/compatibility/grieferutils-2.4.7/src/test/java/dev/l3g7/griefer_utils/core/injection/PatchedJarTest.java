package dev.l3g7.griefer_utils.core.injection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class PatchedJarTest {

    @Test
    public void eventBridgeImplementsEveryCurrentSdkMethod() throws Exception {
        try (JarFile api = new JarFile(System.getProperty("sdkApiJar"));
             JarFile patched = new JarFile(System.getProperty("patchedJar"))) {
            ClassNode contract = readClass(api, "net/labymod/api/event/method/SubscribeMethod.class");
            ClassNode bridge = readClass(patched,
                "dev/l3g7/griefer_utils/labymod/laby4/bridges/LabyBridgeImpl$1.class");
            for (var method : contract.methods) {
                if ((method.access & Opcodes.ACC_ABSTRACT) == 0) continue;
                assertTrue("Missing interface implementation: " + method.name + method.desc,
                    bridge.methods.stream().anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)));
            }
        }
    }

    @Test
    public void optionalBadgeCaptureCannotAbortClientStartup() throws Exception {
        try (JarFile patched = new JarFile(System.getProperty("patchedJar"))) {
            ClassNode node = readClass(patched,
                "dev/l3g7/griefer_utils/core/misc/badges/laby4/Laby4BadgeManager$MixinPlayerListRenderer.class");
            boolean checked = false;
            for (var method : node.methods) {
                if (method.visibleAnnotations == null) continue;
                for (var annotation : method.visibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (annotation.values.get(i).equals("locals")) {
                            assertEquals("CAPTURE_FAILSOFT", ((String[]) annotation.values.get(i + 1))[1]);
                            checked = true;
                        }
                        if (annotation.values.get(i).equals("require") || annotation.values.get(i).equals("expect")) {
                            assertEquals(0, annotation.values.get(i + 1));
                        }
                    }
                }
            }
            assertTrue(checked);
        }
    }

    private static ClassNode readClass(JarFile jar, String path) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = jar.getInputStream(jar.getJarEntry(path))) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void preservesUnrelatedClassesResourcesMetadataAndLicenseExactly() throws Exception {
        try (JarFile upstream = new JarFile(System.getProperty("upstreamJar"));
             JarFile patched = new JarFile(System.getProperty("patchedJar"))) {
            var entries = upstream.entries();
            int verified = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String path = entry.getName();
                if (entry.isDirectory() || path.equals("META-INF/MANIFEST.MF") || path.equals("addon.json")
                    || path.equals("dev/l3g7/griefer_utils/labymod/laby4/bridges/LabyBridgeImpl$1.class")
                    || path.equals("dev/l3g7/griefer_utils/core/misc/badges/laby4/Laby4BadgeManager$MixinPlayerListRenderer.class")
                    || path.matches("dev/l3g7/griefer_utils/core/injection/MixinPlugin(\\$.*)?\\.class")) {
                    continue;
                }
                var result = patched.getJarEntry(path);
                assertNotNull(path, result);
                try (var original = upstream.getInputStream(entry); var output = patched.getInputStream(result)) {
                    assertArrayEquals(path, original.readAllBytes(), output.readAllBytes());
                }
                verified++;
            }
            assertTrue("Should verify the full upstream addon", verified > 1000);
            assertNotNull(patched.getJarEntry("META-INF/SCHLANGEN_SKIPPER_COMPATIBILITY.txt"));
            assertNotNull(patched.getJarEntry("META-INF/compatibility-source/dev/l3g7/griefer_utils/core/injection/MixinPlugin.java"));
            assertEquals(upstream.getManifest().getMainAttributes(), patched.getManifest().getMainAttributes());
        }
    }

    @Test
    public void updaterUsesVersionComparisonWithoutChangingIdentityOrEntrypoints() throws Exception {
        try (JarFile upstream = new JarFile(System.getProperty("upstreamJar"));
             JarFile patched = new JarFile(System.getProperty("patchedJar"))) {
            JsonObject expected = metadata(upstream);
            expected.addProperty("debug", "true");
            expected.addProperty("requiredLabyModBuild", 3231);
            assertEquals(expected, metadata(patched));
        }
    }

    private static JsonObject metadata(JarFile jar) throws Exception {
        try (var input = jar.getInputStream(jar.getJarEntry("addon.json"))) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void filtersBeforeAddingAndCountingMixins() throws Exception {
        try (JarFile patched = new JarFile(System.getProperty("patchedJar"))) {
            var entry = patched.getJarEntry("dev/l3g7/griefer_utils/core/injection/MixinPlugin.class");
            ClassNode node = new ClassNode();
            try (var input = patched.getInputStream(entry)) {
                new ClassReader(input).accept(node, 0);
            }
            var method = node.methods.stream().filter(m -> m.name.equals("getMixins")).findFirst().orElseThrow();
            int guard = -1;
            int add = -1;
            int count = -1;
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i) instanceof MethodInsnNode call) {
                    if (call.name.equals("hasMissingOptionalTarget")) guard = i;
                    if (call.owner.equals("java/util/List") && call.name.equals("add")) add = i;
                    if (call.owner.equals("java/util/concurrent/atomic/AtomicInteger") && call.name.equals("set")) count = i;
                }
            }
            assertTrue(guard >= 0 && guard < add && add < count);
            assertNotNull(patched.getJarEntry("dev/l3g7/griefer_utils/core/injection/OptionalMixinTargets.class"));
        }
    }
}
