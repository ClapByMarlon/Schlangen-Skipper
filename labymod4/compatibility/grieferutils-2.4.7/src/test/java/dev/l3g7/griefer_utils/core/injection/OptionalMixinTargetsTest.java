package dev.l3g7.griefer_utils.core.injection;

import org.junit.Test;

import static org.junit.Assert.*;

public class OptionalMixinTargetsTest {

    @Test
    public void skipsSchematicaWhenRenderOverlayIsAbsent() {
        assertTrue(OptionalMixinTargets.isMissing(
            "com/github/lunatrius/schematica/client/renderer/chunk/overlay/RenderOverlay", target -> false));
    }

    @Test
    public void keepsSchematicaWhenInstalled() {
        assertFalse(OptionalMixinTargets.isMissing(
            "com/github/lunatrius/schematica/client/renderer/chunk/overlay/RenderOverlay", target -> true));
    }

    @Test
    public void skipsOtherSchematicaScreensAndMissingLunatriusCore() {
        for (String target : new String[]{
            "com/github/lunatrius/schematica/client/gui/control/GuiSchematicControl",
            "com/github/lunatrius/schematica/client/gui/load/GuiSchematicLoad",
            "com/github/lunatrius/core/client/renderer/GeometryTessellator"
        }) {
            assertTrue(target, OptionalMixinTargets.isMissing(target, name -> false));
        }
    }

    @Test
    public void skipsForgeOnlyWhenUnavailable() {
        String target = "net/minecraftforge/common/ForgeHooks";
        assertTrue(OptionalMixinTargets.isMissing(target, name -> false));
        assertFalse(OptionalMixinTargets.isMissing(target, name -> true));
    }

    @Test
    public void skipsLegacyCosmeticErrorSuppressorOnlyWhenTargetIsMissing() {
        String target = "net/labymod/user/cosmetic/geometry/effect/effects/GeometryColor";
        assertTrue(OptionalMixinTargets.isMissing(target, name -> false));
        assertFalse(OptionalMixinTargets.isMissing(target, name -> true));
    }

    @Test
    public void skipsRemovedMainMenuSkullHooksOnlyWhenMissing() {
        for (String target : new String[]{"net/labymod/api/Textures$Splash",
            "net/labymod/core/client/render/schematic/Schematic"}) {
            assertTrue(OptionalMixinTargets.isMissing(target, name -> false));
            assertFalse(OptionalMixinTargets.isMissing(target, name -> true));
        }
    }

    @Test
    public void skipsAbsentLaby3OnlyNamespacesButKeepsInstalledTargets() {
        for (String target : new String[]{"net/labymod/core_implementation/mc18/gui/GuiChatAdapter",
            "net/labymod/ingamechat/tabs/GuiChatFilter", "net/labymod/ingamegui/ModuleConfig",
            "net/labymod/accountmanager/storage/LauncherAccount", "net/labymod/settings/elements/ControlElement",
            "net/labymod/user/User", "net/labymod/main/LabyMod", "net/labymod/utils/manager/TagManager"}) {
            assertTrue(target, OptionalMixinTargets.isMissing(target, name -> false));
            assertFalse(target, OptionalMixinTargets.isMissing(target, name -> true));
        }
    }

    @Test
    public void neverSuppressesRequiredMinecraftLabyOrAddonTargets() {
        for (String target : new String[]{"net/minecraft/client/gui/GuiScreen",
            "net/labymod/core/LabyMod", "dev/l3g7/griefer_utils/core/api/SomeClass"}) {
            assertFalse(target, OptionalMixinTargets.isMissing(target, name -> {
                fail("Required targets must not be filtered");
                return false;
            }));
        }
    }
}
