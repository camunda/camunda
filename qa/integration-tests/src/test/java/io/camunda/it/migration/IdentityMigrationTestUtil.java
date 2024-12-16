/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Template for identity migration tests. It starts a Keycloak, Postgres, and the old Management
 * Identity.
 */
@SuppressWarnings("resource")
final class IdentityMigrationTestUtil {
  private static final String POSTGRES_HOST = "postgres";
  private static final int POSTGRES_PORT = 5432;

  private static final int IDENTITY_PORT = 9999;
  private static final String IDENTITY_DATABASE_NAME = "identity";
  private static final String IDENTITY_DATABASE_USERNAME = "identity";
  private static final String IDENTITY_DATABASE_PASSWORD = "t2L@!AqSMg8%I%NmHM";

  private static final String KEYCLOAK_HOST = "keycloak";
  private static final int KEYCLOAK_PORT = 9784;
  private static final String KEYCLOAK_RELATIVE_PATH = "/auth";
  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";

  private static final Network NETWORK = Network.newNetwork();

  static GenericContainer<?> getPostgres() {
    return new GenericContainer<>(DockerImageName.parse("postgres:16.6-alpine"))
        .withNetwork(NETWORK)
        .withNetworkAliases(POSTGRES_HOST)
        .withExposedPorts(POSTGRES_PORT)
        .withEnv("POSTGRES_DB", IDENTITY_DATABASE_NAME)
        .withEnv("POSTGRES_USER", IDENTITY_DATABASE_USERNAME)
        .withEnv("POSTGRES_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  static GenericContainer<?> getKeycloak(final GenericContainer<?> postgres) {
    return new GenericContainer<>("bitnami/keycloak:25.0.2")
        .withNetwork(NETWORK)
        .withNetworkAliases(KEYCLOAK_HOST)
        .withExposedPorts(KEYCLOAK_PORT)
        .dependsOn(postgres)
        .withEnv("KEYCLOAK_HTTP_PORT", Integer.toString(KEYCLOAK_PORT))
        .withEnv("KEYCLOAK_HTTP_RELATIVE_PATH", KEYCLOAK_RELATIVE_PATH)
        .withEnv("KEYCLOAK_ADMIN_USER", KEYCLOAK_USER)
        .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
        .withEnv("KEYCLOAK_DATABASE_HOST", POSTGRES_HOST)
        .withEnv("KEYCLOAK_DATABASE_NAME", IDENTITY_DATABASE_NAME)
        .withEnv("KEYCLOAK_DATABASE_USER", IDENTITY_DATABASE_USERNAME)
        .withEnv("KEYCLOAK_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(KEYCLOAK_PORT)
                .forPath(KEYCLOAK_RELATIVE_PATH)
                .allowInsecure()
                .forStatusCode(200)
                .withReadTimeout(Duration.ofSeconds(10))
                .withStartupTimeout(Duration.ofMinutes(5)));
  }

  static GenericContainer<?> getManagementIdentity(
      final GenericContainer<?> postgres, final GenericContainer<?> keycloak) {
    return new GenericContainer<>(DockerImageName.parse("camunda/identity:SNAPSHOT"))
        .withNetworkAliases("identity")
        .withNetwork(NETWORK)
        .withExposedPorts(8082, IDENTITY_PORT)
        .dependsOn(postgres, keycloak)
        .withEnv("SERVER_PORT", Integer.toString(IDENTITY_PORT))
        .withEnv("KEYCLOAK_URL", internalKeycloakUrl())
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_ISSUER_URL", internalKeycloakUrl() + "/realms/camunda-platform")
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
            internalKeycloakUrl() + "/realms/camunda-platform")
        .withEnv("IDENTITY_DATABASE_HOST", POSTGRES_HOST)
        .withEnv("IDENTITY_DATABASE_PORT", Integer.toString(POSTGRES_PORT))
        .withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME)
        .withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME)
        .withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
        .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
        .withEnv("RESOURCE_PERMISSIONS_ENABLED", "true")
        .withEnv("MULTITENANCY_ENABLED", "true")
        .withEnv("KEYCLOAK_CLIENTS_0_NAME", "migration-app")
        .withEnv("KEYCLOAK_CLIENTS_0_ID", "migration-app")
        .withEnv("KEYCLOAK_CLIENTS_0_SECRET", "secret")
        .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
        .withEnv(
            "KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID",
            "camunda-identity-resource-server")
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write")
        .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
        .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
        .withEnv("MIGRATION", "true")
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(8082)
                .forPath("/actuator/health/readiness")
                .allowInsecure()
                .forStatusCode(200)
                .withReadTimeout(Duration.ofSeconds(10))
                .withStartupTimeout(Duration.ofMinutes(5)));
  }

  private static String internalKeycloakUrl() {
    return "http://%s:%d%s".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT, KEYCLOAK_RELATIVE_PATH);
  }

  static String externalKeycloakUrl(final GenericContainer<?> keycloak) {
    return "http://%s:%d%s"
        .formatted(
            keycloak.getHost(), keycloak.getMappedPort(KEYCLOAK_PORT), KEYCLOAK_RELATIVE_PATH);
  }

  static String externalIdentityUrl(final GenericContainer<?> identity) {
    return "http://%s:%d".formatted(identity.getHost(), identity.getMappedPort(IDENTITY_PORT));
  }

  static String getIdentityAccessToken(final GenericContainer<?> keycloak) {
    final var objectMapper = new ObjectMapper();
    try (final var client = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      externalKeycloakUrl(keycloak)
                          + "/realms/camunda-platform/protocol/openid-connect/token?grant_type=client_credentials"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(
                  BodyPublishers.ofString(
                      "grant_type=client_credentials&client_id=migration-app&client_secret=secret&audience=camunda-identity-resource-server"))
              .build();

      final var response = client.send(request, BodyHandlers.ofString());
      return objectMapper.readTree(response.body()).get("access_token").asText();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to request identity access token", e);
    }
  }
}
