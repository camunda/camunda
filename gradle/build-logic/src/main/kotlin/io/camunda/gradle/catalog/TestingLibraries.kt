package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** Test frameworks, mocks and test infrastructure that is not tied to a specific database. */
internal fun VersionCatalogBuilder.catalogTestingLibraries() {
  library(
      "com-github-dasniko-testcontainers-keycloak",
      "com.github.dasniko",
      "testcontainers-keycloak",
    )
    .versionRef("tc-keycloak")
  // version managed by buildlogic.optimize-conventions
  library("com-icegreen-greenmail", "com.icegreen", "greenmail").withoutVersion()
  library("com-tngtech-archunit-archunit", "com.tngtech.archunit", "archunit")
    .versionRef("archunit")
  library(
      "com-tngtech-archunit-archunit-junit5-api",
      "com.tngtech.archunit",
      "archunit-junit5-api",
    )
    .versionRef("archunit")
  library(
      "com-tngtech-archunit-archunit-junit5-engine",
      "com.tngtech.archunit",
      "archunit-junit5-engine",
    )
    .versionRef("archunit")
  library("eu-rekawek-toxiproxy-toxiproxy-java", "eu.rekawek.toxiproxy", "toxiproxy-java")
    .versionRef("toxiproxy")
  // version managed by buildlogic.optimize-conventions
  library(
      "io-github-netmikey-logunit-logunit-log4j2",
      "io.github.netmikey.logunit",
      "logunit-log4j2",
    )
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library(
      "io-github-netmikey-logunit-logunit-core",
      "io.github.netmikey.logunit",
      "logunit-core",
    )
    .withoutVersion()
  library("io-rest-assured-rest-assured", "io.rest-assured", "rest-assured").withoutVersion()
  library("io-rest-assured-rest-assured-bom", "io.rest-assured", "rest-assured-bom")
    .versionRef("rest-assured")
  library("io-zeebe-zeebe-test-container", "io.zeebe", "zeebe-test-container")
    .versionRef("zeebe-test-container")
  library("io-zeebe-zeebe-test-container-engine", "io.zeebe", "zeebe-test-container-engine")
    .versionRef("zeebe-test-container")
  library("junit-junit", "junit", "junit").versionRef("junit-junit")
  library("net-bytebuddy-byte-buddy", "net.bytebuddy", "byte-buddy")
    .versionRef("net-bytebuddy-byte-buddy")
  library("net-bytebuddy-byte-buddy-agent", "net.bytebuddy", "byte-buddy-agent")
    .versionRef("net-bytebuddy-byte-buddy-agent")
  library("net-jcip-jcip-annotations", "net.jcip", "jcip-annotations").versionRef("jcip")
  library("net-jodah-concurrentunit", "net.jodah", "concurrentunit")
    .versionRef("net-jodah-concurrentunit")
  library("net-jodah-failsafe", "net.jodah", "failsafe").versionRef("net-jodah-failsafe")
  library("dev-failsafe-failsafe", "dev.failsafe", "failsafe").versionRef("dev-failsafe")
  library("net-jqwik-jqwik", "net.jqwik", "jqwik").versionRef("jqwik")
  library("net-jqwik-jqwik-api", "net.jqwik", "jqwik-api").versionRef("jqwik")
  library("org-assertj-assertj-core", "org.assertj", "assertj-core").versionRef("assertj")
  library(
      "org-assertj-assertj-assertions-generator",
      "org.assertj",
      "assertj-assertions-generator",
    )
    .versionRef("assertj-assertions-generator")
  library("org-awaitility-awaitility", "org.awaitility", "awaitility").versionRef("awaitility")
  library("org-hamcrest-hamcrest", "org.hamcrest", "hamcrest").versionRef("hamcrest")
  library("org-instancio-instancio-core", "org.instancio", "instancio-core")
    .versionRef("instancio")
  library("org-javassist-javassist", "org.javassist", "javassist").versionRef("javassist")
  library("org-jeasy-easy-random-core", "org.jeasy", "easy-random-core")
    .versionRef("org-jeasy-easy-random-core")
  library("org-jmock-jmock", "org.jmock", "jmock").versionRef("jmock")
  library("org-junit-jupiter-junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api")
    .withoutVersion()
  library("org-junit-jupiter-junit-jupiter-engine", "org.junit.jupiter", "junit-jupiter-engine")
    .withoutVersion()
  library(
      "org-junit-jupiter-junit-jupiter-migrationsupport",
      "org.junit.jupiter",
      "junit-jupiter-migrationsupport",
    )
    .withoutVersion()
  library("org-junit-jupiter-junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params")
    .withoutVersion()
  library(
      "org-junit-platform-junit-platform-commons",
      "org.junit.platform",
      "junit-platform-commons",
    )
    .withoutVersion()
  library(
      "org-junit-platform-junit-platform-launcher",
      "org.junit.platform",
      "junit-platform-launcher",
    )
    .withoutVersion()
  library(
      "org-junit-platform-junit-platform-suite",
      "org.junit.platform",
      "junit-platform-suite",
    )
    .withoutVersion()
  library(
      "org-junit-platform-junit-platform-suite-api",
      "org.junit.platform",
      "junit-platform-suite-api",
    )
    .withoutVersion()
  library(
      "org-junit-platform-junit-platform-suite-engine",
      "org.junit.platform",
      "junit-platform-suite-engine",
    )
    .withoutVersion()
  library("org-junit-vintage-junit-vintage-engine", "org.junit.vintage", "junit-vintage-engine")
    .withoutVersion()
  library("org-junit-junit-bom", "org.junit", "junit-bom").versionRef("junit")
  // version managed by buildlogic.optimize-conventions
  library("org-mock-server-mockserver-client-java", "org.mock-server", "mockserver-client-java")
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("org-mock-server-mockserver-core", "org.mock-server", "mockserver-core")
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("org-mock-server-mockserver-netty", "org.mock-server", "mockserver-netty")
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library(
      "org-mock-server-mockserver-junit-jupiter",
      "org.mock-server",
      "mockserver-junit-jupiter",
    )
    .withoutVersion()
  library("org-mockito-mockito-core", "org.mockito", "mockito-core").withoutVersion()
  library("org-mockito-mockito-junit-jupiter", "org.mockito", "mockito-junit-jupiter")
    .withoutVersion()
  // version managed by buildlogic.optimize-conventions
  library("org-mockito-mockito-inline", "org.mockito", "mockito-inline").withoutVersion()
  library("org-mockito-mockito-bom", "org.mockito", "mockito-bom").versionRef("mockito")
  library("org-objenesis-objenesis", "org.objenesis", "objenesis").versionRef("objenesis")
  library("org-skyscreamer-jsonassert", "org.skyscreamer", "jsonassert").withoutVersion()
  library("org-testcontainers-testcontainers", "org.testcontainers", "testcontainers")
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-junit-jupiter",
      "org.testcontainers",
      "testcontainers-junit-jupiter",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-localstack",
      "org.testcontainers",
      "testcontainers-localstack",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-toxiproxy",
      "org.testcontainers",
      "testcontainers-toxiproxy",
    )
    .withoutVersion()
  library("org-testcontainers-testcontainers-bom", "org.testcontainers", "testcontainers-bom")
    .versionRef("testcontainers")
  library(
      "org-wiremock-integrations-wiremock-spring-boot",
      "org.wiremock.integrations",
      "wiremock-spring-boot",
    )
    .versionRef("wiremock-spring-boot")
  library("org-wiremock-wiremock-standalone", "org.wiremock", "wiremock-standalone")
    .versionRef("wiremock")
  library("org-xmlunit-xmlunit-core", "org.xmlunit", "xmlunit-core")
    .versionRef("org-xmlunit-xmlunit-core")
}
