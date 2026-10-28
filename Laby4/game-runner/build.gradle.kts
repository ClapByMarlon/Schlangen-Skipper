val sharedSources = tasks.register<Sync>("syncSharedSources") {
    inputs.property("guiInputExceptionAdapter", "vanilla-1.8.9-v2")
    inputs.property("grieferUtilsClassLoaderAdapter", "laby4-addon-service-v1")
    filteringCharset = "UTF-8"
    from("../../Laby3/src/main/java") {
        include("de/laberlucas/botshopgui/update/AddonAutoUpdater.java")
        include("de/laberlucas/botshopgui/util/BotRepository.java")
        include("de/laberlucas/botshopgui/util/BotNameResolver.java")
        include("de/laberlucas/botshopgui/util/GrieferUtilsBridge.java")
        include("de/laberlucas/botshopgui/util/ReadOnlyBotshopGui.java")
        include("de/laberlucas/botshopgui/gui/BotSelectionGui.java")
        filesMatching("**/BotSelectionGui.java") {
            // The vanilla LabyMod 4 mappings omit Forge's checked input exceptions.
            filter { line: String ->
                if (line.contains("protected void keyTyped(") || line.contains("protected void mouseClicked(")
                    || line.contains("public void handleMouseInput(")) {
                    line.replace(" throws IOException", "")
                } else line
            }
        }
        filesMatching("**/GrieferUtilsBridge.java") {
            filter { line: String -> line.replace("Class.forName(",
                "de.laberlucas.botshopgui.GrieferUtilsClasses.load(") }
        }
    }
    into(layout.buildDirectory.dir("generated/shared-java"))
}

sourceSets.named("v1_8_9") {
    java.srcDir(sharedSources)
}

val sharedTests = tasks.register<Sync>("syncSharedTests") {
    from("../../Laby3/src/test/java") {
        include("de/laberlucas/botshopgui/update/AddonAutoUpdaterTest.java")
        include("de/laberlucas/botshopgui/util/ReadOnlyBotshopGuiTest.java")
    }
    into(layout.buildDirectory.dir("generated/shared-test-java"))
}

sourceSets.test {
    java.srcDir(sharedTests)
}

dependencies {
    labyProcessor()
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
    testImplementation(files(sourceSets["v1_8_9"].output))
}

tasks.test {
    dependsOn(rootProject.tasks.named("createReleaseJar"))
    systemProperty("minecraftClasspath", sourceSets["v1_8_9"].compileClasspath.asPath)
    systemProperty("releaseJar", rootProject.layout.buildDirectory.file(
        "libs/schlangen_skipper-release.jar"
    ).get().asFile.absolutePath)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (name == "compileTestJava") {
        options.compilerArgs.add("-proc:none")
    }
}
