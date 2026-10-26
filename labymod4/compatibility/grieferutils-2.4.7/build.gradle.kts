import java.security.MessageDigest

plugins {
    java
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

repositories {
    maven("https://maven.laby.net/api/v1/maven/release/")
    mavenCentral()
}

val upstreamJar = file("../../game-runner/libs/griefer-utils-v2.4.7.jar")
val sdkApi = fileTree(gradle.gradleUserHomeDir.resolve("caches/modules-2/files-2.1/net.labymod.labymod4/api/48-internal-a80b82de")) {
    include("**/*.jar")
}
val transformer = sourceSets.create("transformer")
val verifyUpstream = tasks.register("verifyUpstream") {
    inputs.file(upstreamJar)
    doLast {
        check(sdkApi.files.size == 1) { "Build the LabyMod 4 addon once to populate its pinned SDK API cache" }
        val hash = MessageDigest.getInstance("SHA-256").digest(upstreamJar.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(hash == "55e3decfd6a60ceb8a1308f95a43eade9e80a44da7b0a360cfa051149450f3be") {
            "Input is not the verified official GrieferUtils v2.4.7 release"
        }
    }
}

dependencies {
    compileOnly(files(upstreamJar))
    compileOnly("net.labymod:sponge-mixin:0.5.12+0.17.4+mixin.0.8.7")
    compileOnly("org.ow2.asm:asm-tree:9.9.1")
    compileOnly(files(sdkApi))
    add(transformer.implementationConfigurationName, "org.ow2.asm:asm-tree:9.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation(files(sdkApi))
}

val generatedClasses = layout.buildDirectory.dir("generated/compatibility-classes")
val patchUpstreamClasses = tasks.register<JavaExec>("patchUpstreamClasses") {
    dependsOn(verifyUpstream, tasks.named(transformer.classesTaskName))
    inputs.file(upstreamJar)
    outputs.dir(generatedClasses)
    classpath = transformer.runtimeClasspath
    mainClass.set("compatibility.tools.CompatibilityTransformer")
    args(upstreamJar.absolutePath, generatedClasses.get().asFile.absolutePath)
}
sourceSets.main.get().resources.srcDir(patchUpstreamClasses)

tasks.withType<JavaCompile>().configureEach {
    dependsOn(verifyUpstream)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-proc:none")
}

tasks.jar {
    dependsOn(verifyUpstream)
    archiveFileName.set("griefer-utils-v2.4.7-optional-fix.jar")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(zipTree(upstreamJar)) {
        exclude("META-INF/MANIFEST.MF")
        exclude("addon.json")
        exclude("dev/l3g7/griefer_utils/core/injection/MixinPlugin.class")
        exclude("dev/l3g7/griefer_utils/core/injection/MixinPlugin$*.class")
        exclude("dev/l3g7/griefer_utils/labymod/laby4/bridges/LabyBridgeImpl$1.class")
        exclude("dev/l3g7/griefer_utils/core/misc/badges/laby4/Laby4BadgeManager\$MixinPlayerListRenderer.class")
    }
    from(sourceSets.main.get().allJava) {
        into("META-INF/compatibility-source")
    }
    from(transformer.allJava) {
        into("META-INF/compatibility-source")
    }
    manifest {
        attributes(
            "Game-Version-v1_8_9" to "1.8.9",
            "Game-Mappings-v1_8_9" to "de.oceanlabs:mappings:1.8.9"
        )
    }
}

tasks.test {
    dependsOn(tasks.jar)
    systemProperty("upstreamJar", upstreamJar.absolutePath)
    systemProperty("patchedJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("sdkApiJar", sdkApi.singleFile.absolutePath)
}

tasks.register<JavaExec>("auditLabyTargets") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.l3g7.griefer_utils.core.injection.TargetAudit")
    args(upstreamJar.absolutePath)
    args(fileTree(gradle.gradleUserHomeDir.resolve("caches/modules-2/files-2.1/net.labymod.labymod4")) {
        include("**/*.jar")
    }.files.map { it.absolutePath })
}
