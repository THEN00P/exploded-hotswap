plugins {
  `java-library`
  `maven-publish`
}

dependencies {
  // The tests compile with both compilers, to check the compiler detection against the real thing.
  testImplementation("org.eclipse.jdt:ecj:3.46.100")
}

publishing {
  publications {
    create<MavenPublication>("core") {
      from(components["java"])
    }
  }
}
