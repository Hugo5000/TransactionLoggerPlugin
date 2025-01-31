plugins {
    id("java")
    id("idea")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val githubUsername: String by project
val githubToken: String by project

val pluginName: String by project
val pluginVersion: String by project
val pluginApi: String by project
val pluginDescription: String by project
val pluginGroup: String by project
val pluginArtifact: String by project
val pluginMain: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    // paper-api
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    // EssentialsX
    maven("https://repo.essentialsx.net/releases/")
    // ChestShop
    maven("https://repo.minebench.de")
    // ShopGUI and DeluxeSellWands
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.1")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    compileOnly("at.hugob.plugin.library:gui:0.0.1")
    compileOnly("at.hugob.plugin.library:command:1.1.0")
    compileOnly("at.hugob.plugin.library:config:1.1.2")
    compileOnly("at.hugob.plugin.library:database:1.0.0")

    compileOnly("net.essentialsx:EssentialsX:2.19.7")
    compileOnly("com.acrobot.chestshop:chestshop:3.12")
//    compileOnly("com.github.N0RSKA:DeluxeSellwandsAPI:32c")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.0.0")
    // BeastWithdraw and MoneyFromMobs
    compileOnly(fileTree("./dependencies/"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
//    withSourcesJar()
//    withJavadocJar()
}
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("resources")
        }
    }
    test {
        java {
            srcDir("test")
        }
    }
}
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks.register<Copy>("prepareServer") {
    dependsOn("build")
    from(tasks.jar.get().archiveFile.get().asFile.path)
    rename(tasks.jar.get().archiveFile.get().asFile.name, "$pluginName.jar")
    into("G:\\Minecraft Servers\\paper\\plugins")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
    }
    compileJava {
//        options.compilerArgs.add("-Xlint:unchecked")
//        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }
    compileTestJava { options.encoding = "UTF-8" }
    javadoc { options.encoding = "UTF-8" }
    build { dependsOn(shadowJar) }
    // plugin.yml placeholders
    processResources {
        outputs.upToDateWhen { false }
        filesMatching("**/plugin.yml") {
            expand(
                mapOf(
                    "version" to pluginVersion,
                    "api" to pluginApi,
                    "name" to pluginName,
                    "artifact" to pluginArtifact,
                    "main" to pluginMain,
                    "description" to pluginDescription,
                    "group" to pluginGroup
                )
            )
        }
    }
}
