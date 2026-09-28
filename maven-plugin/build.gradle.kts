plugins {
  id("org.gradlex.maven-plugin-development") version "1.0.3"
  `maven-publish`
}

dependencies {
  implementation(project(":exploded-hotswap-core"))
  // Runs the project's own maven-war-plugin:exploded, with its configuration.
  implementation("org.twdata.maven:mojo-executor:2.4.1")
  // Maven itself provides these at runtime.
  compileOnly("org.apache.maven:maven-plugin-api:3.9.16")
  compileOnly("org.apache.maven:maven-core:3.9.16")
  compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.16.0")
  // The functional tests build the editor's side with the Eclipse compiler, as the editor would.
  testImplementation("org.eclipse.jdt:ecj:3.46.100")
}

mavenPlugin {
  goalPrefix = "exploded-hotswap"
}

publishing {
  publications {
    create<MavenPublication>("mavenPlugin") {
      from(components["java"])
    }
  }
}

tasks.test {
  // The functional tests run a real Maven against the plugin in the local repository.
  dependsOn(":exploded-hotswap-core:publishToMavenLocal", "publishToMavenLocal")
}
