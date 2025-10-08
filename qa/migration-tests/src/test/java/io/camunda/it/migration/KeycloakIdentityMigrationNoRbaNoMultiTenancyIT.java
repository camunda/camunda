/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.IdentityMigrationTestUtil.CAMUNDA_IDENTITY_RESOURCE_SERVER;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT_SECRET;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalIdentityUrl;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalKeycloakUrl;
import static io.camunda.zeebe.qa.util.cluster.TestZeebePort.CLUSTER;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneIdentityMigration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers(parallel = true)
public class KeycloakIdentityMigrationNoRbaNoMultiTenancyIT {
  @TestZeebe(autoStart = false)
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAdditionalProfile("identity")
          .withBasicAuth()
          .withAuthorizationsEnabled();

  @Container
  private static final ElasticsearchContainer ELASTIC = IdentityMigrationTestUtil.getElastic();

  @Container
  private static final KeycloakContainer KEYCLOAK = IdentityMigrationTestUtil.getKeycloak();

  @Container
  private static final GenericContainer<?> POSTGRES = IdentityMigrationTestUtil.getPostgres();

  private static final GenericContainer<?> IDENTITY_87 =
      IdentityMigrationTestUtil.getManagementIdentitySMKeycloak87(KEYCLOAK, POSTGRES)
          // have RBA and multitenancy disabled
          .withEnv("RESOURCE_PERMISSIONS_ENABLED", "false")
          .withEnv("MULTITENANCY_ENABLED", "false")
          // this triggers the setup of the default roles for operate, tasklist and zeebe
          .withEnv("KEYCLOAK_INIT_OPERATE_SECRET", "operate")
          .withEnv("KEYCLOAK_INIT_OPERATE_ROOT_URL", "http://localhost:8081")
          .withEnv("KEYCLOAK_INIT_TASKLIST_SECRET", "tasklist")
          .withEnv("KEYCLOAK_INIT_TASKLIST_ROOT_URL", "http://localhost:8081")
          .withEnv("KEYCLOAK_INIT_ZEEBE_NAME", "zeebe")
          // create groups
          // at least 5 groups are needed to ensure we have no regression on
          // https://github.com/camunda/camunda/issues/39284
          .withEnv("KEYCLOAK_GROUPS_0_NAME", "groupA")
          .withEnv("KEYCLOAK_GROUPS_1_NAME", "groupB")
          .withEnv("KEYCLOAK_GROUPS_2_NAME", "groupC")
          .withEnv("KEYCLOAK_GROUPS_3_NAME", "groupD")
          .withEnv("KEYCLOAK_GROUPS_4_NAME", "groupE")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health/readiness")
                  .allowInsecure()
                  .forStatusCode(200)
                  .withReadTimeout(Duration.ofSeconds(10))
                  .withStartupTimeout(Duration.ofMinutes(5)));

  private static final GenericContainer<?> IDENTITY_88 =
      IdentityMigrationTestUtil.getManagementIdentitySMKeycloak88(KEYCLOAK, POSTGRES)
          // have RBA and multitenancy disabled
          .withEnv("RESOURCE_PERMISSIONS_ENABLED", "false")
          .withEnv("MULTITENANCY_ENABLED", "false");

  protected TestStandaloneIdentityMigration migration;
  protected CamundaClient client;

  @BeforeAll
  static void init() {
    BROKER
        .withCamundaExporter("http://" + ELASTIC.getHttpHostAddress())
        .withProperty(
            "camunda.data.secondary-storage.elasticsearch.url",
            "http://" + ELASTIC.getHttpHostAddress())
        .start();

    IDENTITY_87
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_ISSUER_URL",
            "http://localhost:%d/realms/camunda-platform".formatted(KEYCLOAK.getMappedPort(8080)))
        .start();
    IDENTITY_87.stop();
    IDENTITY_88
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_ISSUER_URL",
            "http://localhost:%d/realms/camunda-platform".formatted(KEYCLOAK.getMappedPort(8080)))
        .start();
  }

  @AfterAll
  static void afterAll() {
    BROKER.stop();
    IDENTITY_87.stop();
    IDENTITY_88.stop();
  }

  @BeforeEach
  public void setup() throws Exception {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setMode(Mode.KEYCLOAK);
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY_88));
    migrationProperties
        .getManagementIdentity()
        .setIssuerBackendUrl(externalKeycloakUrl(KEYCLOAK) + "/realms/camunda-platform/");
    migrationProperties.getManagementIdentity().setIssuerType("KEYCLOAK");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);
    migrationProperties
        .getCluster()
        .setInitialContactPoints(List.of("localhost:" + BROKER.mappedPort(CLUSTER)));
    migration = new TestStandaloneIdentityMigration(migrationProperties);

    client = BROKER.newClientBuilder().build();
  }

  // ensures we have no connection leakage due to https://github.com/camunda/camunda/issues/39284
  @Test
  public void migrationCompletes() {
    // when
    migration.start();

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);
  }
}
