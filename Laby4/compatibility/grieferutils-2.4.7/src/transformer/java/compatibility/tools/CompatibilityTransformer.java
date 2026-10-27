package compatibility.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

public final class CompatibilityTransformer implements Opcodes {

    public static final String BRIDGE = "dev/l3g7/griefer_utils/labymod/laby4/bridges/LabyBridgeImpl$1";
    public static final String BADGES = "dev/l3g7/griefer_utils/core/misc/badges/laby4/Laby4BadgeManager$MixinPlayerListRenderer";
    private static final String FLAGS = "skipperCompat$flags";

    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            ClassNode bridge = read(jar, BRIDGE);
            for (String[] alias : new String[][]{{"eventType", "getEventType"}, {"labyEvent", "getLabyEvent"},
                {"addon", "getAddon"}, {"listener", "getListener"}, {"method", "getMethod"},
                {"priority", "getPriority"}, {"classLoader", "getClassLoader"}}) {
                var old = bridge.methods.stream().filter(m -> m.name.equals(alias[1])).findFirst().orElseThrow();
                MethodNode method = new MethodNode(ACC_PUBLIC, alias[0], old.desc, null, null);
                method.visitVarInsn(ALOAD, 0);
                method.visitMethodInsn(INVOKEVIRTUAL, bridge.name, old.name, old.desc, false);
                method.visitInsn(old.desc.endsWith("B") ? IRETURN : ARETURN);
                bridge.methods.add(method);
            }
            bridge.fields.add(new FieldNode(ACC_PRIVATE | ACC_VOLATILE, FLAGS, "I", null, null));
            MethodNode flags = new MethodNode(ACC_PUBLIC, "flags", "()I", null, null);
            flags.visitVarInsn(ALOAD, 0);
            flags.visitFieldInsn(GETFIELD, bridge.name, FLAGS, "I");
            flags.visitVarInsn(ALOAD, 0);
            flags.visitMethodInsn(INVOKEVIRTUAL, bridge.name, "getEventType", "()Ljava/lang/Class;", false);
            flags.visitVarInsn(ALOAD, 0);
            flags.visitMethodInsn(INVOKEVIRTUAL, bridge.name, "getLabyEvent", "()Lnet/labymod/api/event/LabyEvent;", false);
            flags.visitMethodInsn(INVOKESTATIC, "dev/l3g7/griefer_utils/core/injection/EventMethodFlags", "compute",
                "(Ljava/lang/Class;Lnet/labymod/api/event/LabyEvent;)I", false);
            flags.visitInsn(IOR);
            flags.visitInsn(IRETURN);
            bridge.methods.add(flags);
            MethodNode mark = new MethodNode(ACC_PUBLIC, "markReplayDelivered", "()V", null, null);
            mark.visitVarInsn(ALOAD, 0);
            mark.visitInsn(DUP);
            mark.visitFieldInsn(GETFIELD, bridge.name, FLAGS, "I");
            mark.visitInsn(ICONST_4);
            mark.visitInsn(IOR);
            mark.visitFieldInsn(PUTFIELD, bridge.name, FLAGS, "I");
            mark.visitInsn(RETURN);
            bridge.methods.add(mark);
            write(bridge, Path.of(args[1]));

            ClassNode badges = read(jar, BADGES);
            boolean changed = false;
            for (var method : badges.methods) {
                if (method.visibleAnnotations == null) continue;
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (annotation.values.get(i).equals("locals")) {
                            annotation.values.set(i + 1,
                                new String[]{"Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILSOFT"});
                            changed = true;
                        }
                    }
                    annotation.values.add("require");
                    annotation.values.add(0);
                    annotation.values.add("expect");
                    annotation.values.add(0);
                }
            }
            if (!changed) throw new IllegalStateException("Expected badge local capture was not found");
            write(badges, Path.of(args[1]));
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void write(ClassNode node, Path output) throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path path = output.resolve(node.name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, writer.toByteArray());
    }
}
