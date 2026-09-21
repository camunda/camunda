package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/**
 * Authentication, authorization and cryptography libraries: Keycloak, Nimbus, Auth0, BouncyCastle
 * and the Camunda Identity and security libraries.
 */
internal fun VersionCatalogBuilder.catalogSecurityLibraries() {
  library("com-auth0-java-jwt", "com.auth0", "java-jwt").versionRef("java-jwt")
  library("com-nimbusds-nimbus-jose-jwt", "com.nimbusds", "nimbus-jose-jwt")
    .versionRef("com-nimbusds-nimbus-jose-jwt")
  library("com-nimbusds-oauth2-oidc-sdk", "com.nimbusds", "oauth2-oidc-sdk")
    .versionRef("com-nimbusds-oauth2-oidc-sdk")
  library("com-unboundid-unboundid-ldapsdk", "com.unboundid", "unboundid-ldapsdk")
    .versionRef("com-unboundid-unboundid-ldapsdk")
  library("io-camunda-identity-sdk", "io.camunda", "identity-sdk").versionRef("identity")
  library("io-camunda-security-library-api", "io.camunda", "camunda-security-library-api")
    .versionRef("io-camunda-security-library-api")
  library("io-camunda-security-library-core", "io.camunda", "camunda-security-library-core")
    .versionRef("io-camunda-security-library-api")
  library(
      "io-camunda-security-library-validation",
      "io.camunda",
      "camunda-security-library-validation",
    )
    .versionRef("io-camunda-security-library-api")
  library(
      "io-camunda-security-library-spring-boot-starter",
      "io.camunda",
      "camunda-security-library-spring-boot-starter",
    )
    .versionRef("io-camunda-security-library-api")
  library(
      "io-camunda-identity-spring-boot-autoconfigure",
      "io.camunda",
      "identity-spring-boot-autoconfigure",
    )
    .versionRef("identity")
  library(
      "io-camunda-identity-spring-boot-starter",
      "io.camunda",
      "identity-spring-boot-starter",
    )
    .versionRef("identity")
  library(
      "io-github-acm19-aws-request-signing-apache-interceptor",
      "io.github.acm19",
      "aws-request-signing-apache-interceptor",
    )
    .versionRef("io-github-acm19-aws-request-signing-apache-interceptor")
  library("org-bouncycastle-bcpkix-jdk18on", "org.bouncycastle", "bcpkix-jdk18on")
    .versionRef("bouncycastle")
  library("org-bouncycastle-bcprov-jdk18on", "org.bouncycastle", "bcprov-jdk18on")
    .versionRef("bouncycastle")
  library("org-keycloak-keycloak-admin-client", "org.keycloak", "keycloak-admin-client")
    .versionRef("keycloak-client")
  library(
      "org-keycloak-keycloak-client-common-synced",
      "org.keycloak",
      "keycloak-client-common-synced",
    )
    .versionRef("keycloak-client")
  library("org-keycloak-keycloak-core", "org.keycloak", "keycloak-core").versionRef("keycloak")
}
