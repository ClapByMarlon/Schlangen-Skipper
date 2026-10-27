/*
 * Local compatibility patch for GrieferUtils, licensed under Apache-2.0.
 * Original project: https://github.com/L3g7/GrieferUtils
 */
package dev.l3g7.griefer_utils.core.injection;

import java.util.function.Predicate;

public final class OptionalMixinTargets {

    private OptionalMixinTargets() {
    }

    public static boolean isMissing(String target, Predicate<String> exists) {
        // Required Minecraft/LabyMod targets must continue to fail normally.
        boolean optional = target.startsWith("com/github/lunatrius/")
            || target.startsWith("net/minecraftforge/")
            || target.startsWith("net/labymod/core_implementation/")
            || target.startsWith("net/labymod/ingamechat/")
            || target.startsWith("net/labymod/ingamegui/")
            || target.startsWith("net/labymod/accountmanager/")
            || target.startsWith("net/labymod/settings/")
            || target.startsWith("net/labymod/user/")
            || target.startsWith("net/labymod/main/")
            || target.startsWith("net/labymod/utils/")
            || target.equals("net/labymod/api/Textures$Splash")
            || target.equals("net/labymod/core/client/render/schematic/Schematic");
        return optional && !exists.test(target);
    }
}
