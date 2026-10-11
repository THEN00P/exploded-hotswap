plugins {
  `java-gradle-plugin`
  // The Plugin Portal, for plugins {} blocks; Maven Central as well, with the other modules.
  id("com.gradle.plugin-publish") version "2.2.1"
  id("com.vanniktech.maven.publish")
}

description = "Gradle plugin: unpacks the WAR into build/exploded with the classes your editor compiled, for HotSwap."

dependencies {
  implementation(project(":exploded-hotswap-core"))
  // The functional tests build the editor's side with the Eclipse compiler, as the editor would.
  testImplementation("org.eclipse.jdt:ecj:3.46.100")
}

gradlePlugin {
  website = "https://github.com/THEN00P/exploded-hotswap"
  vcsUrl = "https://github.com/THEN00P/exploded-hotswap.git"
  plugins {
    create("explodedHotswap") {
      id = "io.github.then00p.exploded-hotswap"
      implementationClass = "io.github.then00p.explodedhotswap.ExplodedHotswapPlugin"
      displayName = "Exploded WAR for HotSwap"
      description = "Unpacks the WAR into build/exploded for an IDE server to deploy, with the classes your " +
        "editor compiled, so HotSwap from VS Code, Eclipse, Neovim or Zed works."
      tags = listOf("war", "exploded", "hotswap", "hot-code-replace", "vscode", "wildfly", "debug")
    }
  }
}

tasks.test {
  // The functional tests run Gradle 8 and 9 through TestKit.
  systemProperty("gradle.versions", "8.14.5,9.8.0")
}
