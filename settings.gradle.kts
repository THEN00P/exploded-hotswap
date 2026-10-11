pluginManagement {
  repositories {
    gradlePluginPortal()
    // com.vanniktech.maven.publish is published to Maven Central.
    mavenCentral()
  }
}

rootProject.name = "exploded-hotswap"

// Artifacts: exploded-hotswap-core, exploded-hotswap-gradle-plugin, exploded-hotswap-maven-plugin.
for (module in listOf("core", "gradle-plugin", "maven-plugin")) {
  include(module)
  project(":$module").name = "exploded-hotswap-$module"
  project(":exploded-hotswap-$module").projectDir = file(module)
}
