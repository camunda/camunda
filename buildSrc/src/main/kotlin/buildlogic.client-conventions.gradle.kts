import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("buildlogic.java-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(versionCatalog.findLibrary("com-fasterxml-jackson-jackson-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("com-google-protobuf-protobuf-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("io-grpc-grpc-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("io-micrometer-micrometer-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("io-netty-netty-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("org-mockito-mockito-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()))
    add("implementation", platform(versionCatalog.findLibrary("tools-jackson-jackson-bom").get()))
    add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-core").get())
    add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-slf4j2-impl").get())
}
