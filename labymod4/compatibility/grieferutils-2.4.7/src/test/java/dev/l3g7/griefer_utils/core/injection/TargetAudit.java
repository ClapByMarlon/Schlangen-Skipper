package dev.l3g7.griefer_utils.core.injection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public final class TargetAudit {

    public static void main(String[] args) throws Exception {
        Set<String> available = new HashSet<>();
        for (String arg : args) {
            try (JarFile jar = new JarFile(arg)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    available.add(entries.nextElement().getName());
                }
            }
        }
        try (JarFile jar = new JarFile(args[0])) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (var input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
                }
                if (node.invisibleAnnotations == null) continue;
                for (var annotation : node.invisibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (!List.of("value", "targets").contains(annotation.values.get(i))) continue;
                        for (Object target : (List<?>) annotation.values.get(i + 1)) {
                            String name = target instanceof Type type ? type.getInternalName()
                                : target.toString().replace('.', '/');
                            if (name.startsWith("net/labymod/") && !available.contains(name + ".class")) {
                                System.out.println(node.name + " -> " + name);
                            }
                        }
                    }
                }
            }
        }
    }
}
