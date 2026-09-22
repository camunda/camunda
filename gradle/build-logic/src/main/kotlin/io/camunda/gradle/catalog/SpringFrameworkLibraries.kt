package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** Core Spring Framework, Spring Security and Spring Session libraries. */
internal fun VersionCatalogBuilder.catalogSpringFrameworkLibraries() {
  library(
      "org-springframework-spring-framework-bom",
      "org.springframework",
      "spring-framework-bom",
    )
    .versionRef("spring")
  library(
      "org-springframework-security-spring-security-bom",
      "org.springframework.security",
      "spring-security-bom",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-config",
      "org.springframework.security",
      "spring-security-config",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-core",
      "org.springframework.security",
      "spring-security-core",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-crypto",
      "org.springframework.security",
      "spring-security-crypto",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-oauth2-client",
      "org.springframework.security",
      "spring-security-oauth2-client",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-oauth2-core",
      "org.springframework.security",
      "spring-security-oauth2-core",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-oauth2-jose",
      "org.springframework.security",
      "spring-security-oauth2-jose",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-oauth2-resource-server",
      "org.springframework.security",
      "spring-security-oauth2-resource-server",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-test",
      "org.springframework.security",
      "spring-security-test",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-security-spring-security-web",
      "org.springframework.security",
      "spring-security-web",
    )
    .versionRef("spring-security")
  library(
      "org-springframework-session-spring-session-core",
      "org.springframework.session",
      "spring-session-core",
    )
    .withoutVersion()
  library("org-springframework-spring-aop", "org.springframework", "spring-aop")
    .versionRef("spring")
  library("org-springframework-spring-aspects", "org.springframework", "spring-aspects")
    .versionRef("spring")
  library("org-springframework-spring-beans", "org.springframework", "spring-beans")
    .versionRef("spring")
  library("org-springframework-spring-context", "org.springframework", "spring-context")
    .versionRef("spring")
  library(
      "org-springframework-spring-context-support",
      "org.springframework",
      "spring-context-support",
    )
    .versionRef("spring")
  library("org-springframework-spring-core", "org.springframework", "spring-core")
    .versionRef("spring")
  library("org-springframework-spring-jdbc", "org.springframework", "spring-jdbc")
    .versionRef("spring")
  library("org-springframework-spring-test", "org.springframework", "spring-test")
    .versionRef("spring")
  library("org-springframework-spring-tx", "org.springframework", "spring-tx").versionRef("spring")
  library("org-springframework-spring-web", "org.springframework", "spring-web")
    .versionRef("spring")
  library("org-springframework-spring-webflux", "org.springframework", "spring-webflux")
    .versionRef("spring")
  library("org-springframework-spring-webmvc", "org.springframework", "spring-webmvc")
    .versionRef("spring")
}
