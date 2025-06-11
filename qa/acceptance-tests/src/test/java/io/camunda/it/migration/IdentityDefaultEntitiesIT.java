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
import static org.assertj.core.api.Assertions.tuple;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Group;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneIdentityMigration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
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
public class IdentityDefaultEntitiesIT {

  @TestZeebe(autoStart = false)
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @Container
  private static final ElasticsearchContainer ELASTIC = IdentityMigrationTestUtil.getElastic();

  @Container
  private static final KeycloakContainer KEYCLOAK = IdentityMigrationTestUtil.getKeycloak();

  @Container
  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentity(KEYCLOAK)
          // create groups
          .withEnv("KEYCLOAK_GROUPS_0_NAME", "groupA")
          .withEnv("KEYCLOAK_GROUPS_1_NAME", "groupB")
          .withEnv("KEYCLOAK_GROUPS_2_NAME", "groupC")
          // create users and assign them to groups
          .withEnv("KEYCLOAK_USERS_0_EMAIL", "user0@email.com")
          .withEnv("KEYCLOAK_USERS_0_FIRST-NAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_LAST-NAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_USERNAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_PASSWORD", "password")
          .withEnv("KEYCLOAK_USERS_0_GROUPS_0", "groupA")
          .withEnv("KEYCLOAK_USERS_0_GROUPS_1", "groupB")
          .withEnv("KEYCLOAK_USERS_1_EMAIL", "user1@email.com")
          .withEnv("KEYCLOAK_USERS_1_FIRST-NAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_LAST-NAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_USERNAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_PASSWORD", "password")
          .withEnv("KEYCLOAK_USERS_1_GROUPS_0", "groupC")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health/readiness")
                  .allowInsecure()
                  .forStatusCode(200)
                  .withReadTimeout(Duration.ofSeconds(10))
                  .withStartupTimeout(Duration.ofMinutes(5)));

  private TestStandaloneIdentityMigration standaloneIdentityMigration;
  private CamundaClient client;

  @BeforeAll
  static void init() {
    BROKER.withCamundaExporter("http://" + ELASTIC.getHttpHostAddress()).start();
  }

  @BeforeEach
  public void setup() {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY));
    migrationProperties
        .getManagementIdentity()
        .setIssuerBackendUrl(externalKeycloakUrl(KEYCLOAK) + "/realms/camunda-platform/");
    migrationProperties.getManagementIdentity().setIssuerType("KEYCLOAK");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);
    standaloneIdentityMigration =
        new TestStandaloneIdentityMigration(migrationProperties)
            .withAppConfig(
                config -> {
                  config
                      .getCluster()
                      .setInitialContactPoints(List.of("localhost:" + BROKER.mappedPort(CLUSTER)));
                });

    client = BROKER.newClientBuilder().build();
  }

  @Test
  void canMigrateGroups() {
    // when
    standaloneIdentityMigration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(1000))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groups = client.newGroupsSearchRequest().send().join();
              assertThat(groups.items().size()).isEqualTo(3);
            });

    // then
    final var groups = client.newGroupsSearchRequest().send().join();
    assertThat(groups.items().size()).isEqualTo(3);
    assertThat(groups.items())
        .map(Group::getGroupId, Group::getName)
        .containsExactly(
            tuple("groupa", "groupA"), tuple("groupb", "groupB"), tuple("groupc", "groupC"));
    final var userA = client.newUsersByGroupSearchRequest("groupa").send().join();
    assertThat(userA.items().size()).isEqualTo(1);
    assertThat(userA.items().getFirst().getUsername()).isEqualTo("user0@email.com");
    final var userB = client.newUsersByGroupSearchRequest("groupb").send().join();
    assertThat(userB.items().size()).isEqualTo(1);
    assertThat(userB.items().getFirst().getUsername()).isEqualTo("user0@email.com");
    final var userC = client.newUsersByGroupSearchRequest("groupc").send().join();
    assertThat(userC.items().size()).isEqualTo(1);
    assertThat(userC.items().getFirst().getUsername()).isEqualTo("user1@email.com");
  }
}
