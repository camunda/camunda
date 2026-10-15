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
val springBootVersion = versionCatalog.requiredVersion("spring-boot")
val micrometerVersion = versionCatalog.requiredVersion("micrometer")

// Module coordinates pinned by the parent POM, mapped to the catalog entry holding their version.
val forcedModules =
  mapOf(
      "co.elastic.clients:elasticsearch-java" to "co-elastic-clients-elasticsearch-java",
      "com.google.code.gson:gson" to "gson",
      "org.elasticsearch.client:elasticsearch-rest-client" to "elasticsearch",
      "com.google.guava:guava" to "guava",
      "com.google.api.grpc:proto-google-common-protos" to
        "com-google-api-grpc-proto-google-common-protos",
      "com.jayway.jsonpath:json-path" to "com-jayway-jsonpath-json-path",
      "net.bytebuddy:byte-buddy" to "net-bytebuddy-byte-buddy",
      "org.jetbrains:annotations" to "org-jetbrains-annotations",
      "org.keycloak:keycloak-admin-client" to "keycloak-client",
      "org.keycloak:keycloak-client-common-synced" to "keycloak-client",
      "org.objenesis:objenesis" to "objenesis",
      "org.ow2.asm:asm" to "asm",
      "commons-codec:commons-codec" to "commons-codec",
      "commons-collections:commons-collections" to "org-apache-commons-commons-collections",
      "commons-logging:commons-logging" to "commons-logging",
      "org.apache.commons:commons-lang3" to "org-apache-commons-commons-lang3",
      "com.auth0:auth0" to "auth0",
      "com.auth0:jwks-rsa" to "jwks-rsa",
      "com.nimbusds:nimbus-jose-jwt" to "com-nimbusds-nimbus-jose-jwt",
      "com.squareup.okio:okio-jvm" to "okio-jvm",
      "org.yaml:snakeyaml" to "org-yaml-snakeyaml",
      "jakarta.xml.bind:jakarta.xml.bind-api" to "jakarta-xml-bind-jakarta-xml-bind-api",
      "net.java.dev.jna:jna" to "jna",
      "net.java.dev.jna:jna-platform" to "jna-platform",
      "org.apache.httpcomponents.client5:httpclient5" to
        "org-apache-httpcomponents-client5-httpclient5",
      "org.apache.httpcomponents:httpclient" to "org-apache-httpcomponents-httpclient",
      "org.apache.httpcomponents.core5:httpcore5" to "org-apache-httpcomponents-core5-httpcore5",
      "org.apache.httpcomponents.core5:httpcore5-h2" to
        "org-apache-httpcomponents-core5-httpcore5",
      "org.apache.tomcat.embed:tomcat-embed-el" to "tomcat",
      "org.apache.tomcat.embed:tomcat-embed-websocket" to "tomcat",
      "org.checkerframework:checker-qual" to "org-checkerframework-checker-qual",
      "org.codehaus.mojo:animal-sniffer-annotations" to "animal-sniffer",
      "org.javassist:javassist" to "javassist",
      "org.jetbrains.kotlin:kotlin-stdlib" to "kotlin-stdlib",
      "com.google.errorprone:error_prone_annotations" to
        "com-google-errorprone-error-prone-core",
      "org.slf4j:slf4j-api" to "slf4j",
    )
    .map { (module, versionKey) -> "$module:${versionCatalog.requiredVersion(versionKey)}" }
    .toTypedArray()

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
configurations.configureEach {
  resolutionStrategy.force(*forcedModules)
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
