/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Dependency management and global test dependencies inherited from the `zeebe-parent` POM.
 *
 * Every module that inherits `zeebe-parent` — the server modules directly, the client modules
 * through `camunda-library-parent`, which adds no dependency management of its own — resolves the
 * same imported BOMs, forced versions, and global test dependencies. Keep this the single source
 * for that behaviour so the server and client conventions cannot drift apart.
 */

import buildlogic.requiredVersion
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("buildlogic.java-conventions") }

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val esJavaVersion = versionCatalog.requiredVersion("co-elastic-clients-elasticsearch-java")
val elasticsearchVersion = versionCatalog.requiredVersion("elasticsearch")
val asmVersion = versionCatalog.requiredVersion("asm")
val animalSnifferVersion = versionCatalog.requiredVersion("animal-sniffer")
val byteBuddyVersion = versionCatalog.requiredVersion("net-bytebuddy-byte-buddy")
val httpcore5Version = versionCatalog.requiredVersion("org-apache-httpcomponents-core5-httpcore5")
val httpclient5Version =
  versionCatalog.requiredVersion("org-apache-httpcomponents-client5-httpclient5")
val httpclientVersion = versionCatalog.requiredVersion("org-apache-httpcomponents-httpclient")
val springBootVersion = versionCatalog.requiredVersion("spring-boot")
val micrometerVersion = versionCatalog.requiredVersion("micrometer")
val slf4jVersion = versionCatalog.requiredVersion("slf4j")
val snakeyamlVersion = versionCatalog.requiredVersion("org-yaml-snakeyaml")
val auth0Version = versionCatalog.requiredVersion("auth0")
val errorProneVersion = versionCatalog.requiredVersion("com-google-errorprone-error-prone-core")
val jsonPathVersion = versionCatalog.requiredVersion("com-jayway-jsonpath-json-path")
val jwksRsaVersion = versionCatalog.requiredVersion("jwks-rsa")
val okioJvmVersion = versionCatalog.requiredVersion("okio-jvm")
val nimbusJoseJwtVersion = versionCatalog.requiredVersion("com-nimbusds-nimbus-jose-jwt")
val jetbrainsAnnotationsVersion = versionCatalog.requiredVersion("org-jetbrains-annotations")
val keycloakClientVersion = versionCatalog.requiredVersion("keycloak-client")
val checkerQualVersion = versionCatalog.requiredVersion("org-checkerframework-checker-qual")
val commonsCodecVersion = versionCatalog.requiredVersion("commons-codec")
val commonsCollectionsVersion =
  versionCatalog.requiredVersion("org-apache-commons-commons-collections")
val commonsLoggingVersion = versionCatalog.requiredVersion("commons-logging")
val gsonVersion = versionCatalog.requiredVersion("gson")
val guavaVersion = versionCatalog.requiredVersion("guava")
val jakartaXmlBindVersion = versionCatalog.requiredVersion("jakarta-xml-bind-jakarta-xml-bind-api")
val javassistVersion = versionCatalog.requiredVersion("javassist")
val jnaVersion = versionCatalog.requiredVersion("jna")
val jnaPlatformVersion = versionCatalog.requiredVersion("jna-platform")
val kotlinStdlibVersion = versionCatalog.requiredVersion("kotlin-stdlib")
val objenesisVersion = versionCatalog.requiredVersion("objenesis")
val tomcatVersion = versionCatalog.requiredVersion("tomcat")

dependencies {
  // Mirror the parent POM's imported BOMs. Platforms add version constraints only; they do not
  // add the managed libraries to a module's runtime classpath.
  val importedBoms =
    listOf(
      "com-azure-azure-sdk-bom",
      "com-fasterxml-jackson-jackson-bom",
      "com-google-cloud-libraries-bom",
      "com-google-protobuf-protobuf-bom",
      "io-github-openfeign-feign-bom",
      "io-grpc-grpc-bom",
      "io-micrometer-micrometer-bom",
      "io-modelcontextprotocol-sdk-mcp-bom",
      "io-netty-netty-bom",
      "io-opentelemetry-opentelemetry-bom",
      "io-rest-assured-rest-assured-bom",
      "org-junit-junit-bom",
      "org-mockito-mockito-bom",
      "org-springframework-ai-spring-ai-bom",
      "org-springframework-boot-spring-boot-dependencies",
      "org-springframework-spring-framework-bom",
      "org-springframework-security-spring-security-bom",
      "org-testcontainers-testcontainers-bom",
      "software-amazon-awssdk-bom",
      "tools-jackson-jackson-bom",
      "org-apache-logging-log4j-log4j-bom",
    )
  importedBoms.forEach { add("implementation", platform(versionCatalog.findLibrary(it).get())) }
  // log4j-bom also manages the annotation processor path.
  add(
    "annotationProcessor",
    platform(versionCatalog.findLibrary("org-apache-logging-log4j-log4j-bom").get()),
  )
  // zeebe-parent declares these as global test-scoped dependencies, so every inheriting module
  // (server and client alike) gets them.
  add("testRuntimeOnly", versionCatalog.findLibrary("org-apache-logging-log4j-log4j-core").get())
  add(
    "testRuntimeOnly",
    versionCatalog.findLibrary("org-apache-logging-log4j-log4j-slf4j2-impl").get(),
  )
}

// Force versions in the same way they are pinned by maven.
// Either explicitly or implicitly by the order of the dependencies being listed in the pom.
configurations.all {
  resolutionStrategy.force(
    "co.elastic.clients:elasticsearch-java:$esJavaVersion",
    "com.google.code.gson:gson:$gsonVersion",
    "org.elasticsearch.client:elasticsearch-rest-client:$elasticsearchVersion",
    "com.google.guava:guava:$guavaVersion",
    "com.jayway.jsonpath:json-path:$jsonPathVersion",
    "net.bytebuddy:byte-buddy:$byteBuddyVersion",
    "org.jetbrains:annotations:$jetbrainsAnnotationsVersion",
    "org.keycloak:keycloak-admin-client:$keycloakClientVersion",
    "org.keycloak:keycloak-client-common-synced:$keycloakClientVersion",
    "org.objenesis:objenesis:$objenesisVersion",
    "org.ow2.asm:asm:$asmVersion",
    "commons-codec:commons-codec:$commonsCodecVersion",
    "commons-collections:commons-collections:$commonsCollectionsVersion",
    "commons-logging:commons-logging:$commonsLoggingVersion",
    "com.auth0:auth0:$auth0Version",
    "com.auth0:jwks-rsa:$jwksRsaVersion",
    "com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion",
    "com.squareup.okio:okio-jvm:$okioJvmVersion",
    "org.yaml:snakeyaml:$snakeyamlVersion",
    "jakarta.xml.bind:jakarta.xml.bind-api:$jakartaXmlBindVersion",
    "net.java.dev.jna:jna:$jnaVersion",
    "net.java.dev.jna:jna-platform:$jnaPlatformVersion",
    "org.apache.httpcomponents.client5:httpclient5:$httpclient5Version",
    "org.apache.httpcomponents:httpclient:$httpclientVersion",
    "org.apache.httpcomponents.core5:httpcore5:$httpcore5Version",
    "org.apache.httpcomponents.core5:httpcore5-h2:$httpcore5Version",
    "org.apache.tomcat.embed:tomcat-embed-el:$tomcatVersion",
    "org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion",
    "org.checkerframework:checker-qual:$checkerQualVersion",
    "org.codehaus.mojo:animal-sniffer-annotations:$animalSnifferVersion",
    "org.javassist:javassist:$javassistVersion",
    "org.jetbrains.kotlin:kotlin-stdlib:$kotlinStdlibVersion",
    "com.google.errorprone:error_prone_annotations:$errorProneVersion",
    "org.slf4j:slf4j-api:$slf4jVersion",
  )
  resolutionStrategy.eachDependency {
    if (requested.group == "org.springframework.boot") {
      useVersion(springBootVersion)
    }
    // context-propagation has independent versioning; only pin the BOM-managed artifacts.
    if (
      requested.group == "io.micrometer" &&
        requested.name.startsWith("micrometer-") &&
        requested.name != "micrometer-bom"
    ) {
      useVersion(micrometerVersion)
    }
  }
  // The global test-scoped log4j-slf4j2-impl (SLF4J → Log4j) conflicts with
  // spring-boot-starter-logging (logback and log4j-to-slf4j, Log4j → SLF4J), creating a circular
  // bridge. Exclude it globally; modules that need Logback for Spring @WebMvcTest must exclude
  // log4j-slf4j2-impl from their testRuntimeClasspath instead.
  exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
  // tomcat-annotations-api is a compile-time artifact inside tomcat-embed-core; not needed
  // at runtime. Maven excludes it explicitly in every module that pulls tomcat-embed-core.
  exclude(group = "org.apache.tomcat", module = "tomcat-annotations-api")
}
