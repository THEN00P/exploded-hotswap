plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

description = "The part of exploded-hotswap both plugins share: finds the classes the editor compiled and lays out the exploded WAR."

dependencies {
  // The tests compile with both compilers, to check the compiler detection against the real thing.
  testImplementation("org.eclipse.jdt:ecj:3.46.100")
}

