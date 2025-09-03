/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

/** Template for identity migration tests. It starts a Keycloak and the old Management Identity. */
@SuppressWarnings("resource")
final class IdentityMigrationTestUtil {
  public static final String IDENTITY_CLIENT = "migration-app";
  public static final String IDENTITY_CLIENT_SECRET = "secret";
  public static final String CAMUNDA_IDENTITY_RESOURCE_SERVER = "camunda-identity-resource-server";
  public static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  public static final int IDENTITY_PORT = 8080;
  public static final String LATEST_87 = "8.7.6";
  private static final String KEYCLOAK_HOST = "keycloak";
  private static final int KEYCLOAK_PORT = 8080;
  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";
  private static final Network NETWORK = Network.newNetwork();
  private static final String POSTGRES_HOST = "postgres";
  private static final int POSTGRES_PORT = 5432;
  private static final String IDENTITY_DATABASE_NAME = "identity";
  private static final String IDENTITY_DATABASE_USERNAME = "identity";
  private static final String IDENTITY_DATABASE_PASSWORD = "t2L@!AqSMg8%I%NmHM";

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

  static ElasticsearchContainer getElastic() {
    return TestSearchContainers.createDefeaultElasticsearchContainer()
        .withNetwork(NETWORK)
        .withNetworkAliases("elastic")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

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

  static GenericContainer<?> getManagementIdentitySMKeycloak(
      final KeycloakContainer keycloak, final GenericContainer<?> postgres) {
    return new GenericContainer<>(DockerImageName.parse("camunda/identity:" + LATEST_87))
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .dependsOn(keycloak)
        .dependsOn(postgres)
        .withEnv("SERVER_PORT", Integer.toString(IDENTITY_PORT))
        .withEnv("RESOURCE_PERMISSIONS_ENABLED", "true")
        .withEnv("MULTITENANCY_ENABLED", "true")
        .withEnv("KEYCLOAK_URL", "http://%s:%d".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
            "http://%s:%d/realms/camunda-platform".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
        .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
        .withEnv("KEYCLOAK_CLIENTS_0_NAME", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_ID", IDENTITY_CLIENT)
        .withEnv("KEYCLOAK_CLIENTS_0_SECRET", IDENTITY_CLIENT_SECRET)
        .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
        .withEnv(
            "KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", CAMUNDA_IDENTITY_RESOURCE_SERVER)
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write")
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_1_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
        .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_1_DEFINITION", "write:*")
        .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
        .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
        // this will enable readiness checks by spring to await ApplicationRunner completion
        .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
        .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
        // postgres configuration
        .withEnv("IDENTITY_DATABASE_HOST", POSTGRES_HOST)
        .withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME)
        .withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .withEnv("IDENTITY_DATABASE_PORT", Integer.toString(POSTGRES_PORT))
        .withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME)
        .withNetworkAliases("identity")
        .withNetwork(NETWORK)
        .withExposedPorts(IDENTITY_PORT, 8082)
        .withLogConsumer(
            new Slf4jLogConsumer(LoggerFactory.getLogger(IdentityMigrationTestUtil.class)));
  }

  static GenericContainer<?> getManagementIdentitySMOidc(
      final KeycloakContainer keycloak, final GenericContainer<?> postgres) {
    return new GenericContainer<>(DockerImageName.parse("camunda/identity:" + LATEST_87))
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .dependsOn(keycloak)
        .dependsOn(postgres)
        .withEnv("SPRING_PROFILES_ACTIVE", "oidc")
        .withEnv("SERVER_PORT", Integer.toString(IDENTITY_PORT))
        .withEnv("MULTITENANCY_ENABLED", "true")
        .withEnv("CAMUNDA_IDENTITY_TYPE", "GENERIC")
        .withEnv("CAMUNDA_IDENTITY_BASE_URL", "http://identity:8080")
        .withEnv(
            "CAMUNDA_IDENTITY_ISSUER",
            "http://%s:%d/realms/oidc-camunda-platform".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv(
            "CAMUNDA_IDENTITY_ISSUER_BACKEND_URL",
            "http://%s:%d/realms/oidc-camunda-platform".formatted(KEYCLOAK_HOST, KEYCLOAK_PORT))
        .withEnv("CAMUNDA_IDENTITY_CLIENT_ID", IDENTITY_CLIENT)
        .withEnv("CAMUNDA_IDENTITY_CLIENT_SECRET", IDENTITY_CLIENT_SECRET)
        .withEnv("CAMUNDA_IDENTITY_AUDIENCE", CAMUNDA_IDENTITY_RESOURCE_SERVER)
        .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
        .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
        // this will enable readiness checks by spring to await ApplicationRunner completion
        .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
        .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
        // postgres configuration
        .withEnv("IDENTITY_DATABASE_HOST", POSTGRES_HOST)
        .withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME)
        .withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .withEnv("IDENTITY_DATABASE_PORT", Integer.toString(POSTGRES_PORT))
        .withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME)
        .withEnv("IDENTITY_MAPPING_RULES_0_NAME", "Rule 1")
        .withEnv("IDENTITY_MAPPING_RULES_0_CLAIM_NAME", "client_id")
        .withEnv("IDENTITY_MAPPING_RULES_0_CLAIM_VALUE", IDENTITY_CLIENT)
        .withEnv("IDENTITY_MAPPING_RULES_0_OPERATOR", "EQUALS")
        .withEnv("IDENTITY_MAPPING_RULES_0_RULE_TYPE", "ROLE")
        .withEnv("IDENTITY_MAPPING_RULES_0_APPLIED_ROLE_NAMES_0", "Identity")
        .withNetworkAliases("identity")
        .withNetwork(NETWORK)
        .withExposedPorts(IDENTITY_PORT, 8082)
        .withLogConsumer(
            new Slf4jLogConsumer(LoggerFactory.getLogger(IdentityMigrationTestUtil.class)));
  }

  static GenericContainer<?> getManagementIdentitySaaS(final GenericContainer<?> postgres) {
    return new GenericContainer<>(DockerImageName.parse("camunda/identity:" + LATEST_87))
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .dependsOn(postgres)
        .withEnv("SERVER_PORT", Integer.toString(IDENTITY_PORT))
        .withEnv("RESOURCE_PERMISSIONS_ENABLED", "true")
        .withEnv("SPRING_PROFILES_ACTIVE", "saas")
        .withEnv("IDENTITY_AUDIENCE", "identity")
        .withEnv("IDENTITY_DATABASE_HOST", POSTGRES_HOST)
        .withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME)
        .withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD)
        .withEnv("IDENTITY_DATABASE_PORT", Integer.toString(POSTGRES_PORT))
        .withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME)
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
