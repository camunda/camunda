rootProject.name = "build-logic"

val parentPom = file("../../parent/pom.xml").readText()
val junitVersion =
  Regex("<version\\.junit>([^<]+)</version\\.junit>")
    .find(parentPom)
    ?.groupValues
    ?.get(1)
    ?: error("Missing version.junit in parent/pom.xml")

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      version("junit", junitVersion)
      library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
      library("junit-platform-launcher", "org.junit.platform", "junit-platform-launcher")
        .withoutVersion()
    }
  }
}

include("pom-resolution")
