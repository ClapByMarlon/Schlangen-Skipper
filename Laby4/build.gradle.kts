plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

group = "de.laberlucas.botshopgui"
version = "1.0.2"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

labyMod {
    defaultPackageName = "de.laberlucas.botshopgui"
    minecraft {
        registerVersion("1.8.9") {}
    }
    addonInfo {
        namespace = "schlangen_skipper"
        displayName = "Schlangen Skipper"
        author = "ClapByMarlon"
        description = "Du kannst die Botshop-GUI von überall öffnen."
        minecraftVersion = "1.8.9"
        version = rootProject.version.toString()
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")
    group = rootProject.group
    version = rootProject.version
}

val exportAddon = tasks.register("exportAddon") {
    dependsOn("createReleaseJar")
    val source = layout.buildDirectory.file("libs/schlangen_skipper-release.jar")
    val target = layout.projectDirectory.file("schlangen-skipper-laby4-${project.version}.jar")
    inputs.file(source)
    outputs.file(target)
    doLast {
        source.get().asFile.copyTo(target.asFile, overwrite = true)
    }
}

tasks.named("build") {
    dependsOn(exportAddon)
}
