pluginManagement {
  repositories {
    gradlePluginPortal()
    // com.vanniktech.maven.publish is published to Maven Central.
    mavenCentral()
  }
}

plugins {
  // Downloads the JDK 21 toolchain when this machine has none Gradle can find.
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "exploded-hotswap"

// Artifacts: exploded-hotswap-core, exploded-hotswap-gradle-plugin, exploded-hotswap-maven-plugin.
for (module in listOf("core", "gradle-plugin", "maven-plugin")) {
  include(module)
  project(":$module").name = "exploded-hotswap-$module"
  project(":exploded-hotswap-$module").projectDir = file(module)
}
