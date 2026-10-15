rootProject.name = "build-logic"

val parentPom =
  providers.fileContents(layout.settingsDirectory.file("../../parent/pom.xml")).asText.get()

fun pomProperty(name: String): String =
  Regex("<${Regex.escape(name)}>([^<]+)</${Regex.escape(name)}>")
    .find(parentPom)
    ?.groupValues
    ?.get(1)
    ?: error("Missing $name in parent/pom.xml")

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      version("junit", pomProperty("version.junit"))
      library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
      library("junit-platform-launcher", "org.junit.platform", "junit-platform-launcher")
        .withoutVersion()
      library(
        "assertj-assertions-generator",
        "org.assertj",
        "assertj-assertions-generator",
      )
        .version(pomProperty("version.assertj-assertions-generator"))
      library(
        "openapi-generator-plugin",
        "org.openapitools",
        "openapi-generator-gradle-plugin",
      )
        .version(pomProperty("plugin.version.openapi-generator"))
    }
  }
}

include("pom-resolution")

include("conventions")
