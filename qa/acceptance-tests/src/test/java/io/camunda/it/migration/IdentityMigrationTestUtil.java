/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

/** Template for identity migration tests. It starts a Keycloak and the old Management Identity. */
@SuppressWarnings("resource")
final class IdentityMigrationTestUtil {
  public static final String IDENTITY_CLIENT = "migration-app";
  public static final String IDENTITY_CLIENT_SECRET = "secret";
  public static final String CAMUNDA_IDENTITY_RESOURCE_SERVER = "camunda-identity-resource-server";
  public static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  private static final int IDENTITY_PORT = 8080;
  private static final String KEYCLOAK_HOST = "keycloak";
  private static final int KEYCLOAK_PORT = 8080;
  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";
  private static final Network NETWORK = Network.newNetwork();

  static KeycloakContainer getKeycloak() {
    return new KeycloakContainer()
        .withEnv("KEYCLOAK_ADMIN", KEYCLOAK_USER)
        .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
        .withEnv("KEYCLOAK_DATABASE_VENDOR", "dev-mem")
        .withNetwork(NETWORK)
        .withNetworkAliases(KEYCLOAK_HOST)
        .withExposedPorts(KEYCLOAK_PORT, 9000)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(8080)
                .forPath("/")
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
  }

  static GenericContainer<?> getManagementIdentity(final KeycloakContainer keycloak) {
    return new GenericContainer<>(DockerImageName.parse("camunda/identity:SNAPSHOT"))
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .dependsOn(keycloak)
        .withEnv("SERVER_PORT", Integer.toString(IDENTITY_PORT))
        .withEnv("KEYCLOAK_URL", "http://%s:%d".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
            "http://%s:%d/realms/camunda-platform".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
        .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
        .withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", IDENTITY_CLIENT_SECRET)
        .withEnv("KEYCLOAK_CLIENTS_0_NAME", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_ID", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_SECRET", IDENTITY_CLIENT_SECRET)
        .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
        .withEnv(
            "KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", CAMUNDA_IDENTITY_RESOURCE_SERVER)
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write")
        .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
        .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
        // this will enable readiness checks by spring to await ApplicationRunner completion
        .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
        .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
        .withNetworkAliases("identity")
        .withNetwork(NETWORK)
        .withExposedPorts(IDENTITY_PORT, 8082)
        .withLogConsumer(
            new Slf4jLogConsumer(LoggerFactory.getLogger(IdentityMigrationTestUtil.class)));
  }

  static String externalKeycloakUrl(final GenericContainer<?> keycloak) {
    return "http://%s:%d".formatted(keycloak.getHost(), keycloak.getMappedPort(KEYCLOAK_PORT));
  }

  static String externalIdentityUrl(final GenericContainer<?> identity) {
    return "http://%s:%d".formatted(identity.getHost(), identity.getMappedPort(IDENTITY_PORT));
  }
}
