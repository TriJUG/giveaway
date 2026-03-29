plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
    id("com.palantir.java-format") version "2.50.0"
}

group = "dev.trijug"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

val tamboVersion = "0.2.0-SNAPSHOT"

dependencies {
    implementation(platform("dev.tamboui:tamboui-bom:$tamboVersion"))
    implementation("dev.tamboui:tamboui-toolkit")
    implementation("dev.tamboui:tamboui-jline3-backend")
}

application {
    mainClass = "dev.trijug.giveaway.Main"
    applicationName = "trijug"
    applicationDefaultJvmArgs = listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseEpsilonGC"
    )
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "trijug"
            mainClass = "dev.trijug.giveaway.Main"
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--gc=epsilon")
        }
    }
}
