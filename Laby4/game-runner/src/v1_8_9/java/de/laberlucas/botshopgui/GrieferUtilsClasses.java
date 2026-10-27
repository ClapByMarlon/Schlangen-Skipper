package de.laberlucas.botshopgui;

import net.labymod.api.Laby;

public final class GrieferUtilsClasses {

    private GrieferUtilsClasses() {
    }

    public static Class<?> load(String name) throws ClassNotFoundException {
        var addon = Laby.labyAPI().addonService().getAddon("griefer_utils")
            .orElseThrow(() -> new ClassNotFoundException("GrieferUtils is not installed"));
        return addon.getClassLoader().loadClass(name);
    }
}
