plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"

    id("net.mamoe.mirai-console") version "2.15.0-RC"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
}

group = "xyz.cssxsh.mirai"
version = "0.1.2"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("cssxsh", "mirai-hibernate-forward")
    licenseFromGitHubProject("AGPL-3.0")
    workingDir = System.getenv("PUBLICATION_TEMP")?.let { file(it).resolve(projectName) }
        ?: buildDir.resolve("publishing-tmp")
    publication {
        artifact(tasks["buildPlugin"])
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.7.1")
    testImplementation(kotlin("test"))
    //
    implementation(platform("net.mamoe:mirai-bom:2.15.0-RC"))
    testImplementation("net.mamoe:mirai-logging-slf4j")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.7"))
    testImplementation("org.slf4j:slf4j-simple")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}
