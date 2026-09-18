import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    id("java")
}

group = "net.yamakotaro.autoupdater"
version = project.property("pluginVersion") as String

val paperApiVersion: String by project

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    // Paper / Purpur が実行時に提供するため compileOnly。
    // Gson も Paper サーバー本体にバンドルされているため追加依存は不要。
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.matching("Eclipse Adoptium"))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("AutoUpdater")
    archiveVersion.set(project.version.toString())
}

tasks.test {
    useJUnitPlatform()
}
