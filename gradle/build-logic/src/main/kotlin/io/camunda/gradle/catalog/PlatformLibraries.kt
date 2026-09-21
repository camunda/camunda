package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** Runtime platform libraries: networking, serialization, observability and build tooling. */
internal fun VersionCatalogBuilder.catalogPlatformLibraries() {
  library("com-esotericsoftware-kryo", "com.esotericsoftware", "kryo")
    .versionRef("com-esotericsoftware-kryo")
  library("com-esotericsoftware-minlog", "com.esotericsoftware", "minlog")
    .versionRef("com-esotericsoftware-minlog")
  library("com-github-jnr-jnr-constants", "com.github.jnr", "jnr-constants")
    .versionRef("com-github-jnr-jnr-constants")
  library("com-github-jnr-jnr-ffi", "com.github.jnr", "jnr-ffi")
    .versionRef("com-github-jnr-jnr-ffi")
  library("com-github-jnr-jnr-posix", "com.github.jnr", "jnr-posix")
    .versionRef("com-github-jnr-jnr-posix")
  library("com-github-luben-zstd-jni", "com.github.luben", "zstd-jni")
    .versionRef("com-github-luben-zstd-jni")
  // version managed by buildlogic.optimize-conventions
  library("com-github-sisyphsu-dateparser", "com.github.sisyphsu", "dateparser")
    .withoutVersion()
  library("com-google-protobuf-protobuf-java", "com.google.protobuf", "protobuf-java")
    .withoutVersion()
  library("com-google-protobuf-protobuf-java-util", "com.google.protobuf", "protobuf-java-util")
    .withoutVersion()
  library("com-google-protobuf-protobuf-bom", "com.google.protobuf", "protobuf-bom")
    .versionRef("protobuf")
  library(
      "com-netflix-concurrency-limits-concurrency-limits-core",
      "com.netflix.concurrency-limits",
      "concurrency-limits-core",
    )
    .versionRef("netflix-concurrency")
  library("io-github-openfeign-feign-core", "io.github.openfeign", "feign-core")
    .withoutVersion()
  library("io-github-openfeign-feign-httpclient", "io.github.openfeign", "feign-httpclient")
    .withoutVersion()
  library("io-github-openfeign-feign-jackson", "io.github.openfeign", "feign-jackson")
    .withoutVersion()
  library("io-github-openfeign-feign-bom", "io.github.openfeign", "feign-bom")
    .versionRef("feign")
  library(
      "io-github-resilience4j-resilience4j-core",
      "io.github.resilience4j",
      "resilience4j-core",
    )
    .versionRef("resilience4j")
  library(
      "io-github-resilience4j-resilience4j-retry",
      "io.github.resilience4j",
      "resilience4j-retry",
    )
    .versionRef("resilience4j")
  library("io-grpc-grpc-api", "io.grpc", "grpc-api").withoutVersion()
  library("io-grpc-grpc-core", "io.grpc", "grpc-core").withoutVersion()
  library("io-grpc-grpc-inprocess", "io.grpc", "grpc-inprocess").withoutVersion()
  library("io-grpc-grpc-netty", "io.grpc", "grpc-netty").withoutVersion()
  library("io-grpc-grpc-protobuf", "io.grpc", "grpc-protobuf").withoutVersion()
  library("io-grpc-grpc-services", "io.grpc", "grpc-services").withoutVersion()
  library("io-grpc-grpc-stub", "io.grpc", "grpc-stub").withoutVersion()
  library("io-grpc-grpc-testing", "io.grpc", "grpc-testing").withoutVersion()
  library("io-grpc-grpc-util", "io.grpc", "grpc-util").withoutVersion()
  library("io-grpc-grpc-bom", "io.grpc", "grpc-bom").versionRef("grpc")
  library("io-micrometer-micrometer-commons", "io.micrometer", "micrometer-commons")
    .withoutVersion()
  library("io-micrometer-micrometer-core", "io.micrometer", "micrometer-core").withoutVersion()
  library("io-micrometer-micrometer-observation", "io.micrometer", "micrometer-observation")
    .withoutVersion()
  library("io-micrometer-micrometer-registry-otlp", "io.micrometer", "micrometer-registry-otlp")
    .withoutVersion()
  library(
      "io-micrometer-micrometer-registry-prometheus",
      "io.micrometer",
      "micrometer-registry-prometheus",
    )
    .withoutVersion()
  library("io-micrometer-micrometer-bom", "io.micrometer", "micrometer-bom")
    .versionRef("micrometer")
  library("io-modelcontextprotocol-sdk-mcp-core", "io.modelcontextprotocol.sdk", "mcp-core")
    .withoutVersion()
  library(
      "io-modelcontextprotocol-sdk-mcp-json-jackson3",
      "io.modelcontextprotocol.sdk",
      "mcp-json-jackson3",
    )
    .withoutVersion()
  library("io-modelcontextprotocol-sdk-mcp-bom", "io.modelcontextprotocol.sdk", "mcp-bom")
    .versionRef("mcp-sdk")
  library("io-netty-netty-buffer", "io.netty", "netty-buffer").withoutVersion()
  library("io-netty-netty-codec-base", "io.netty", "netty-codec-base").withoutVersion()
  library("io-netty-netty-codec-compression", "io.netty", "netty-codec-compression")
    .withoutVersion()
  library("io-netty-netty-codec-dns", "io.netty", "netty-codec-dns").withoutVersion()
  library("io-netty-netty-codec-http", "io.netty", "netty-codec-http").withoutVersion()
  library("io-netty-netty-common", "io.netty", "netty-common").withoutVersion()
  library("io-netty-netty-handler", "io.netty", "netty-handler").withoutVersion()
  library("io-netty-netty-resolver", "io.netty", "netty-resolver").withoutVersion()
  library("io-netty-netty-resolver-dns", "io.netty", "netty-resolver-dns").withoutVersion()
  library(
      "io-netty-netty-tcnative-boringssl-static",
      "io.netty",
      "netty-tcnative-boringssl-static",
    )
    .withoutVersion()
  library("io-netty-netty-transport", "io.netty", "netty-transport").withoutVersion()
  library("io-netty-netty-transport-classes-epoll", "io.netty", "netty-transport-classes-epoll")
    .withoutVersion()
  library("io-netty-netty-transport-native-epoll", "io.netty", "netty-transport-native-epoll")
    .withoutVersion()
  library("io-netty-netty-bom", "io.netty", "netty-bom").versionRef("netty")
  library("io-projectreactor-reactor-core", "io.projectreactor", "reactor-core")
    .withoutVersion()
  library("io-opentelemetry-opentelemetry-bom", "io.opentelemetry", "opentelemetry-bom")
    .versionRef("opentelemetry")
  library("io-opentelemetry-opentelemetry-api", "io.opentelemetry", "opentelemetry-api")
    .withoutVersion()
  library("io-opentelemetry-opentelemetry-sdk", "io.opentelemetry", "opentelemetry-sdk")
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-sdk-common",
      "io.opentelemetry",
      "opentelemetry-sdk-common",
    )
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-sdk-metrics",
      "io.opentelemetry",
      "opentelemetry-sdk-metrics",
    )
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-sdk-logs",
      "io.opentelemetry",
      "opentelemetry-sdk-logs",
    )
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-sdk-testing",
      "io.opentelemetry",
      "opentelemetry-sdk-testing",
    )
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-exporter-otlp",
      "io.opentelemetry",
      "opentelemetry-exporter-otlp",
    )
    .withoutVersion()
  library(
      "io-opentelemetry-opentelemetry-exporter-sender-jdk",
      "io.opentelemetry",
      "opentelemetry-exporter-sender-jdk",
    )
    .withoutVersion()
  library(
      "io-prometheus-prometheus-metrics-exporter-httpserver",
      "io.prometheus",
      "prometheus-metrics-exporter-httpserver",
    )
    .versionRef("prometheus")
  library("io-prometheus-prometheus-metrics-model", "io.prometheus", "prometheus-metrics-model")
    .versionRef("prometheus")
  library(
      "io-swagger-core-v3-swagger-annotations-jakarta",
      "io.swagger.core.v3",
      "swagger-annotations-jakarta",
    )
    .versionRef("io-swagger-core-v3-swagger-annotations-jakarta")
  library(
      "io-swagger-core-v3-swagger-models-jakarta",
      "io.swagger.core.v3",
      "swagger-models-jakarta",
    )
    .versionRef("io-swagger-core-v3-swagger-annotations-jakarta")
  library(
      "io-swagger-parser-v3-swagger-parser-core",
      "io.swagger.parser.v3",
      "swagger-parser-core",
    )
    .versionRef("swagger-parser")
  library("io-swagger-parser-v3-swagger-parser-v3", "io.swagger.parser.v3", "swagger-parser-v3")
    .versionRef("swagger-parser")
  library("org-agrona-agrona", "org.agrona", "agrona").versionRef("agrona")
  library("org-apache-logging-log4j-log4j-api", "org.apache.logging.log4j", "log4j-api")
    .withoutVersion()
  library("org-apache-logging-log4j-log4j-core", "org.apache.logging.log4j", "log4j-core")
    .withoutVersion()
  library(
      "org-apache-logging-log4j-log4j-core-test",
      "org.apache.logging.log4j",
      "log4j-core-test",
    )
    .withoutVersion()
  library(
      "org-apache-logging-log4j-log4j-layout-template-json",
      "org.apache.logging.log4j",
      "log4j-layout-template-json",
    )
    .withoutVersion()
  library(
      "org-apache-logging-log4j-log4j-slf4j2-impl",
      "org.apache.logging.log4j",
      "log4j-slf4j2-impl",
    )
    .withoutVersion()
  library("org-apache-logging-log4j-log4j-bom", "org.apache.logging.log4j", "log4j-bom")
    .versionRef("log4j")
  library(
      "org-apache-maven-surefire-maven-surefire-common",
      "org.apache.maven.surefire",
      "maven-surefire-common",
    )
    .versionRef("org-apache-maven-surefire-maven-surefire-common")
  library(
      "org-apache-maven-surefire-surefire-myextensions-api",
      "org.apache.maven.surefire",
      "surefire-extensions-api",
    )
    .versionRef("org-apache-maven-surefire-surefire-myextensions-api")
  // version managed by buildlogic.optimize-conventions
  library(
      "org-glassfish-jersey-core-jersey-client",
      "org.glassfish.jersey.core",
      "jersey-client",
    )
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library(
      "org-glassfish-jersey-media-jersey-media-json-jackson",
      "org.glassfish.jersey.media",
      "jersey-media-json-jackson",
    )
    .withoutVersion()
  library("org-openjdk-jmh-jmh-core", "org.openjdk.jmh", "jmh-core").versionRef("jmh")
  library(
      "org-openjdk-jmh-jmh-generator-annprocess",
      "org.openjdk.jmh",
      "jmh-generator-annprocess",
    )
    .versionRef("jmh")
  library("org-reactivestreams-reactive-streams", "org.reactivestreams", "reactive-streams")
    .versionRef("org-reactivestreams-reactive-streams")
  library("org-ow2-asm-asm", "org.ow2.asm", "asm").versionRef("asm")
  library("org-scala-lang-scala-library", "org.scala-lang", "scala-library").versionRef("scala")
  library("org-slf4j-slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
  library("org-yaml-snakeyaml", "org.yaml", "snakeyaml").versionRef("org-yaml-snakeyaml")
}
