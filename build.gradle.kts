import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom-remap")
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
}

version = providers.gradleProperty("mod_version").get()

group = providers.gradleProperty("maven_group").get()

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from
    // automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("mi84_calc") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

fabricApi { configureDataGeneration { client = true } }

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    modImplementation(
            "net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}"
    )

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation(
            "net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}"
    )
    modImplementation(
            "net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}"
    )

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    compileClasspath += sourceSets.getByName("client").output
    runtimeClasspath += sourceSets.getByName("client").output
}

val testWorkingDirectory = layout.buildDirectory.dir("test-run")
tasks.test {
    useJUnitPlatform()
    val workingDirectory = testWorkingDirectory.get().asFile
    doFirst { workingDirectory.mkdirs() }
    workingDir(workingDirectory)
}

val verifyCalculatorArchitecture by tasks.registering {
    group = "verification"
    description = "Checks the calculator's Minecraft boundary and required maintenance documents."
    val calculatorSource = file("src/client/kotlin/net/amathboi/mi84mod/client/calculator")
    val nonMinecraftLayers = listOf("input", "controller", "ui")
        .map { calculatorSource.resolve(it) }
    val widgetSource = calculatorSource.resolve("CalculatorWidget.kt")
    val architectureDocument = file("ARCHITECTURE.md")
    val buttonMatrix = file("BUTTON_MATRIX.md")
    val readme = file("README.md")
    val changelog = file("CHANGELOG.md")
    val featureStatus = file("FEATURE_STATUS.md")
    val releaseVersion = project.version.toString()
    inputs.property("releaseVersion", releaseVersion)
    inputs.files(
        fileTree(calculatorSource),
        architectureDocument,
        buttonMatrix,
        readme,
        changelog,
        featureStatus
    )

    doLast {
        nonMinecraftLayers.flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.forEach { sourceFile ->
            check("net.minecraft" !in sourceFile.readText()) {
                "${sourceFile.relativeTo(projectDir)} crosses the documented Minecraft boundary"
            }
        }
        check(widgetSource.readLines().size <= 300) {
            "CalculatorWidget.kt must remain a thin adapter; extract behavior before it exceeds 300 lines"
        }
        check(architectureDocument.isFile && buttonMatrix.isFile) {
            "ARCHITECTURE.md and BUTTON_MATRIX.md are required maintenance contracts"
        }
        check("Version $releaseVersion" in readme.readText()) {
            "README.md must identify release version $releaseVersion"
        }
        check("version $releaseVersion" in featureStatus.readText()) {
            "FEATURE_STATUS.md must identify release version $releaseVersion"
        }
        check("## $releaseVersion -" in changelog.readText()) {
            "CHANGELOG.md must contain a release section for $releaseVersion"
        }
    }
}

tasks.check {
    dependsOn(verifyCalculatorArchitecture)
}

tasks.processResources {
    val version = version
    inputs.property("version", version)

    filesMatching("fabric.mod.json") { expand("version" to version) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") { rename { "${it}_$projectName" } }
}

// configure the maven publication
publishing {
    publications { register<MavenPublication>("mavenJava") { from(components["java"]) } }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to
    // set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}

// Fixes strict task ordering validation errors with Gradle 9+ and Split Sources
tasks.matching { it.name.startsWith("compile") }.configureEach {
    mustRunAfter(tasks.matching { it.name.startsWith("gen") })
}
