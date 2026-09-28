// Shared by the three modules: coordinates, Java 11 bytecode (Gradle 8 and Maven 3.9 still run on
// Java 11), JUnit, and local publishing for trying the plugins in other builds.
subprojects {
  group = "io.github.then00p"
  version = "0.1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
      toolchain {
        languageVersion = JavaLanguageVersion.of(17)
      }
    }
    tasks.withType<JavaCompile>().configureEach {
      options.release = 11
    }
    dependencies {
      "testImplementation"(platform("org.junit:junit-bom:5.10.3"))
      "testImplementation"("org.junit.jupiter:junit-jupiter")
      "testImplementation"("org.junit.jupiter:junit-jupiter-params")
      "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
    }
  }
}
