import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
  id("org.gradlex.maven-plugin-development") version "1.0.3"
  id("com.vanniktech.maven.publish")
}

description = "Maven plugin: mvn exploded-hotswap:exploded lays out target/exploded with the classes your editor compiled, for HotSwap."

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

mavenPublishing {
  configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
}

tasks.test {
  // The functional tests run a real Maven against the plugin in the local repository.
  dependsOn(":exploded-hotswap-core:publishToMavenLocal", "publishToMavenLocal")
}
