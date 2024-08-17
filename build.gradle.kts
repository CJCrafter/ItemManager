import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.breadmoirai.github-release") version "2.4.1"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    kotlin("jvm") version "1.9.23"
}

group = "com.cjcrafter"
version = "1.0.0"

bukkit {
    main = "com.cjcrafter.itemmanager.ItemManager"
    apiVersion = "1.13"

    authors = listOf("CJCrafter")
    depend = listOf("MechanicsCore")
}

repositories {
    mavenCentral()

    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot
    maven(url = "https://repo.jeff-media.com/public/") // SpigotUpdateChecker
}

dependencies {
    implementation("com.jeff_media:SpigotUpdateChecker:3.0.3")

    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("com.cjcrafter:mechanicscore:3.4.1")

    testImplementation(kotlin("test"))
}

tasks.shadowJar {
    archiveFileName.set("ItemManager-${project.version}.jar")
    destinationDirectory.set(file("release"))

    dependencies {
        relocate ("org.bstats", "com.cjcrafter.itemmanager.lib.bstats") {
            include(dependency("org.bstats:"))
        }
        relocate("com.jeff_media", "com.cjcrafter.itemmanager.lib") {
            include(dependency("com.jeff_media:"))
        }

        relocate ("kotlin.", "me.deecaad.weaponmechanics.lib.kotlin.")
    }
}

githubRelease {
    owner.set("CJCrafter")
    repo.set("ItemManager")
    authorization.set("Token ${findProperty("pass").toString()}")
    tagName.set("v$version")
    targetCommitish.set("master")
    releaseName.set("v$version")
    draft.set(false)
    prerelease.set(false)
    generateReleaseNotes.set(true)
    body.set("")
    overwrite.set(false)
    allowUploadToExisting.set(false)
    apiEndpoint.set("https://api.github.com")

    setReleaseAssets(file("release").listFiles())

    // If set to true, you can debug that this would do
    dryRun.set(false)
}

tasks.register("preRelease").configure {
    val folder = file("release")
    folder.mkdirs()
    for (file in folder.listFiles()!!) {
        if ("MechanicsCore" !in file.name)
            file.delete()
    }

    dependsOn(":shadowJar")
    finalizedBy("zipForRelease")
}

tasks.register<Zip>("zipForRelease") {
    dependsOn("preRelease")
    archiveFileName.set("ItemManager.zip")
    destinationDirectory.set(file("release"))

    from(file("release")) {
        include("*.jar")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.release.set(16)
}
kotlin {
    jvmToolchain(21)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "16"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "16"
}
