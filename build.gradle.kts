import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

// Shared by the three modules: coordinates, Java 11 bytecode (Gradle 8 and Maven 3.9 still run on
// Java 11), JUnit, and publishing to Maven Central with the POM it requires.
subprojects {
  group = "io.github.then00p"
  // The release workflow passes the tag's version; every other build is a snapshot.
  version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

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

  plugins.withId("com.vanniktech.maven.publish") {
    extensions.configure<MavenPublishBaseExtension> {
      publishToMavenCentral()
      // A release signs in-process with the armored key in signingInMemoryKey and its passphrase
      // in signingInMemoryKeyPassword (ORG_GRADLE_PROJECT_ variables in CI), so no gpg program
      // and no passphrase prompt are involved. Snapshots stay unsigned.
      if (!version.toString().endsWith("SNAPSHOT")) {
        check(providers.gradleProperty("signingInMemoryKey").isPresent) {
          "a release needs signingInMemoryKey and signingInMemoryKeyPassword"
        }
        signAllPublications()
      }
      pom {
        name = project.name
        description = provider { project.description }
        url = "https://github.com/THEN00P/exploded-hotswap"
        inceptionYear = "2026"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution = "repo"
          }
        }
        developers {
          developer {
            id = "THEN00P"
            name = "THEN00P"
            url = "https://github.com/THEN00P"
          }
        }
        scm {
          url = "https://github.com/THEN00P/exploded-hotswap"
          connection = "scm:git:https://github.com/THEN00P/exploded-hotswap.git"
          developerConnection = "scm:git:ssh://git@github.com/THEN00P/exploded-hotswap.git"
        }
      }
    }
  }
}
