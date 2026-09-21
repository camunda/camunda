import buildlogic.requiredVersion
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("buildlogic.java-conventions") }

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val commonsLoggingVersion = versionCatalog.requiredVersion("commons-logging")
val snakeyamlVersion = versionCatalog.requiredVersion("org-yaml-snakeyaml")

dependencies {
  val importedBoms =
    listOf(
      "com-fasterxml-jackson-jackson-bom",
      "com-google-protobuf-protobuf-bom",
      "io-grpc-grpc-bom",
      "io-micrometer-micrometer-bom",
      "io-netty-netty-bom",
      "org-mockito-mockito-bom",
      "org-apache-logging-log4j-log4j-bom",
      "tools-jackson-jackson-bom",
    )
  importedBoms.forEach { add("implementation", platform(versionCatalog.findLibrary(it).get())) }
  add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-core").get())
  add(
    "testRuntimeOnly",
    versionCatalog.findLibrary("org-apache-logging-log4j-log4j-slf4j2-impl").get(),
  )
}

configurations.all {
  resolutionStrategy.force(
    "commons-logging:commons-logging:$commonsLoggingVersion",
    "org.yaml:snakeyaml:$snakeyamlVersion",
  )
}
