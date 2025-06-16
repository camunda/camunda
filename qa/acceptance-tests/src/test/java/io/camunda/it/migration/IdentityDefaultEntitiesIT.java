/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.camunda.it.migration.IdentityMigrationTestUtil.CAMUNDA_IDENTITY_RESOURCE_SERVER;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT_SECRET;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalIdentityUrl;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalKeycloakUrl;
import static io.camunda.migration.identity.config.saas.StaticEntities.DEVELOPER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.OPERATIONS_ENGINEER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_IDS;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;
import static io.camunda.migration.identity.config.saas.StaticEntities.TASK_USER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.VISITOR_ROLE_ID;
import static io.camunda.zeebe.qa.util.cluster.TestZeebePort.CLUSTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.console.ConsoleClient.Role;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneIdentityMigration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@WireMockTest
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

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private TestStandaloneIdentityMigration standaloneIdentityMigration;
  private CamundaClient client;

  @BeforeAll
  static void init() {
    BROKER.withCamundaExporter("http://" + ELASTIC.getHttpHostAddress()).start();
  }

  @BeforeEach
  public void setup(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    // given
    stubConsoleClient();

    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY));
    migrationProperties
        .getManagementIdentity()
        .setIssuerBackendUrl(externalKeycloakUrl(KEYCLOAK) + "/realms/camunda-platform/");
    migrationProperties.getManagementIdentity().setIssuerType("KEYCLOAK");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);
    // Console properties
    migrationProperties.setOrganizationId("org123");
    migrationProperties.getConsole().setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
    migrationProperties
        .getConsole()
        .setIssuerBackendUrl(wmRuntimeInfo.getHttpBaseUrl() + "/oauth/token");
    migrationProperties.getConsole().setClientId("client-id");
    migrationProperties.getConsole().setClientSecret("client-secret");
    migrationProperties.getConsole().setAudience("test-audience");
    migrationProperties.getConsole().setClusterId("cluster123");
    migrationProperties.getConsole().setInternalClientId("client123");

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
        .atMost(Duration.ofSeconds(5))
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

  @Test
  public void canMigrateRoles() {
    // when
    standaloneIdentityMigration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roles = client.newRolesSearchRequest().send().join();
              assertThat(roles.items())
                  .map(io.camunda.client.api.search.response.Role::getRoleId)
                  .contains(
                      Role.DEVELOPER.getName(),
                      Role.OPERATIONS_ENGINEER.getName(),
                      Role.TASK_USER.getName(),
                      Role.VISITOR.getName());
            });

    // then
    final var roles = client.newRolesSearchRequest().send().join();
    assertThat(roles.items())
        .map(
            io.camunda.client.api.search.response.Role::getRoleId,
            io.camunda.client.api.search.response.Role::getName)
        .contains(
            tuple(Role.DEVELOPER.getName(), "Developer"),
            tuple(Role.OPERATIONS_ENGINEER.getName(), "Operations Engineer"),
            tuple(Role.TASK_USER.getName(), "Task User"),
            tuple(Role.VISITOR.getName(), "Visitor"));
    final var members = client.newUsersByRoleSearchRequest("admin").send().join();
    assertThat(members.items()).map(RoleUser::getUsername).contains("user@example.com");
    final var members2 = client.newUsersByRoleSearchRequest("developer").send().join();
    assertThat(members2.items().size()).isEqualTo(1);
    assertThat(members2.items().getFirst().getUsername()).isEqualTo("user@example.com");
  }

  @Test
  public void canMigrateRolePermissions()
      throws URISyntaxException, IOException, InterruptedException {
    // when
    standaloneIdentityMigration.start();
    final var restAddress = client.getConfiguration().getRestAddress().toString();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = searchAuthorizations(restAddress);
              final var migratedAuthorizations =
                  authorizations.items().stream()
                      .map(AuthorizationResponse::ownerId)
                      .filter(ROLE_IDS::contains)
                      .toList();
              assertThat(migratedAuthorizations.size()).isEqualTo(ROLE_PERMISSIONS.size());
            });

    // then
    final var authorizations = searchAuthorizations(restAddress);
    assertThat(authorizations.items())
        .extracting(
            AuthorizationResponse::ownerId,
            AuthorizationResponse::ownerType,
            AuthorizationResponse::resourceId,
            AuthorizationResponse::resourceType,
            AuthorizationResponse::permissionTypes)
        .contains(
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                TASK_USER_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                TASK_USER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_USER_TASK)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)));
  }

  private void stubConsoleClient() {
    final String token = "mocked-access-token";

    stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\": \"" + token + "\"}")));

    final String endpoint =
        MessageFormat.format(
            "/external/organizations/{0}/clusters/{1}/migrationData/{2}",
            "org123", "cluster123", "client123");

    final String responseJson =
        """
        {
          "members": [
            {
              "originalUserId": "user123",
              "roles": ["owner", "developer"],
              "email": "user@example.com",
              "name": "John Doe"
            }
          ],
          "clients": [
            {
              "name": "console-client",
              "clientId": "client123",
              "permissions": ["Operate", "Zeebe"]
            }
          ]
        }
        """;

    stubFor(
        get(urlEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));
  }

  // TODO: refactor this once https://github.com/camunda/camunda/issues/32721 is implemented
  private static AuthorizationSearchResponse searchAuthorizations(final String restAddress)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted("demo", "demo").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/authorizations/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), AuthorizationSearchResponse.class);
  }

  private record AuthorizationSearchResponse(List<AuthorizationResponse> items) {}

  private record AuthorizationResponse(
      String ownerId,
      OwnerType ownerType,
      ResourceType resourceType,
      String resourceId,
      Set<PermissionType> permissionTypes,
      String authorizationKey) {}
}
