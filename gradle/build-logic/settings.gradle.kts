rootProject.name = "build-logic"

// Google-hosted Maven Central mirror first: shared CI/sandbox egress IPs get rate limited (HTTP
// 429) by repo.maven.apache.org. The mirror lags Central and lacks portal-only plugins, so the
// portal comes next; Central stays last because Gradle disables a repository after a 429, and a
// throttled Central answers 429 even for artifacts it does not host.
pluginManagement {
  repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2")
    gradlePluginPortal()
  }
}

val parentPom =
  providers.fileContents(layout.settingsDirectory.file("../../parent/pom.xml")).asText.get()

fun pomProperty(name: String): String =
  Regex("<${Regex.escape(name)}>([^<]+)</${Regex.escape(name)}>")
    .find(parentPom)
    ?.groupValues
    ?.get(1)
    ?: error("Missing $name in parent/pom.xml")

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

  repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2")
    gradlePluginPortal()
    mavenCentral()
  }

  versionCatalogs {
    create("libs") {
      version("junit", pomProperty("version.junit"))
      version("assertj") { strictly(pomProperty("version.assertj")) }
      library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj")
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
