/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Template for identity migration tests. It starts a Keycloak, Postgres, and the old Management
 * Identity.
 */
@SuppressWarnings("resource")
final class IdentityMigrationTestUtil {
  public static final String IDENTITY_CLIENT = "migration-app";
  public static final String IDENTITY_CLIENT_SECRET = "secret";
  public static final String CAMUNDA_IDENTITY_RESOURCE_SERVER = "camunda-identity-resource-server";
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
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("postgres:16.6-alpine")))
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
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("bitnami/keycloak:25.0.2")))
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
        .withExposedPorts(8082, IDENTITY_PORT, 5005)
        .dependsOn(postgres, keycloak)
        .withEnv(
            "JAVA_TOOL_OPTIONS",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:"
                + 5005) // Pass debug options
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
        .withEnv("KEYCLOAK_CLIENTS_0_NAME", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_ID", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_SECRET", IDENTITY_CLIENT_SECRET)
        .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
        .withEnv(
            "KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", CAMUNDA_IDENTITY_RESOURCE_SERVER)
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write")
        .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
        .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
        .withEnv("MIGRATION", "true")
        .withLogConsumer(
            new Slf4jLogConsumer(LoggerFactory.getLogger(IdentityMigrationTestUtil.class)))
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
}
