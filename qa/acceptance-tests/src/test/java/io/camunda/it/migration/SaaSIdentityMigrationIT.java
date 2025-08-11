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
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.camunda.it.migration.IdentityMigrationTestUtil.CAMUNDA_IDENTITY_RESOURCE_SERVER;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT_SECRET;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalIdentityUrl;
import static io.camunda.migration.identity.config.saas.StaticEntities.DEVELOPER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.OPERATIONS_ENGINEER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_IDS;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;
import static io.camunda.migration.identity.config.saas.StaticEntities.TASK_USER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.VISITOR_ROLE_ID;
import static io.camunda.zeebe.qa.util.cluster.TestZeebePort.CLUSTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.migration.identity.client.ConsoleClient.Role;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneIdentityMigration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
public class SaaSIdentityMigrationIT {

  public static final String CONSOLE_CLIENT_ID = "client-id";
  public static final String CONSOLE_CLIENT_SECRET = "client-secret";
  public static final String CONSOLE_AUDIENCE = "test-audience";

  @TestZeebe(autoStart = false)
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String AUTH_TOKEN_BODY =
      """
      {
          "client_id":"%s",
          "client_secret":"%s",
          "audience":"%s",
          "grant_type":"client_credentials"
        }
      """;
  private static final String TOKEN = loadFixture("jwt-identity-client.txt");

  @Container
  private static final ElasticsearchContainer ELASTIC = IdentityMigrationTestUtil.getElastic();

  @Container
  private static final GenericContainer<?> POSTGRES = IdentityMigrationTestUtil.getPostgres();

  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentitySaaS(POSTGRES)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health/readiness")
                  .allowInsecure()
                  .forStatusCode(200)
                  .withReadTimeout(Duration.ofSeconds(10))
                  .withStartupTimeout(Duration.ofMinutes(5)));
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();
  private TestStandaloneIdentityMigration migration;
  private CamundaClient client;

  @BeforeAll
  static void init(final WireMockRuntimeInfo wmRuntimeInfo) {
    BROKER.withCamundaExporter("http://" + ELASTIC.getHttpHostAddress()).start();

    org.testcontainers.Testcontainers.exposeHostPorts(wmRuntimeInfo.getHttpPort());
    IDENTITY.withEnv(
        "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
        "http://host.testcontainers.internal:%d/".formatted(wmRuntimeInfo.getHttpPort()));
    IDENTITY.withEnv(
        "IDENTITY_AUTH_PROVIDER_ISSUER_URL",
        "http://host.testcontainers.internal:%d/".formatted(wmRuntimeInfo.getHttpPort()));
    IDENTITY.start();
  }

  @BeforeEach
  public void setup(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    // given
    stubConsoleClient();

    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY));
    migrationProperties.getManagementIdentity().setIssuerBackendUrl(wmRuntimeInfo.getHttpBaseUrl());
    migrationProperties.getManagementIdentity().setIssuerType("AUTH0");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);
    // Console properties
    migrationProperties.setOrganizationId("org123");
    migrationProperties.getConsole().setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
    migrationProperties
        .getConsole()
        .setIssuerBackendUrl(wmRuntimeInfo.getHttpBaseUrl() + "/oauth/token");
    migrationProperties.getConsole().setClientId(CONSOLE_CLIENT_ID);
    migrationProperties.getConsole().setClientSecret(CONSOLE_CLIENT_SECRET);
    migrationProperties.getConsole().setAudience(CONSOLE_AUDIENCE);
    migrationProperties.getConsole().setClusterId("cluster123");
    migrationProperties.getConsole().setInternalClientId("client123");

    migration =
        new TestStandaloneIdentityMigration(migrationProperties)
            .withAppConfig(
                config -> {
                  config
                      .getCluster()
                      .setInitialContactPoints(List.of("localhost:" + BROKER.mappedPort(CLUSTER)));
                });

    client = BROKER.newClientBuilder().build();
  }

  @AfterAll
  static void cleanup() {
    IDENTITY.stop();
  }

  @AfterEach
  public void afterEach() {
    if (migration != null) {
      migration.close();
    }
    if (client != null) {
      client.close();
    }
  }

  @Test
  void canMigrateGroups() throws IOException, URISyntaxException, InterruptedException {
    // when
    createGroups();
    assignGroupsToUsers();

    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groups = client.newGroupsSearchRequest().send().join();
              assertThat(groups.items().size()).isEqualTo(3);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

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
    migration.start();

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
    assertThat(migration.getExitCode()).isEqualTo(0);

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
    assertThat(members.items())
        .map(RoleUser::getUsername)
        .contains("user0@email.com", "user1@email.com");
    final var members2 = client.newUsersByRoleSearchRequest("developer").send().join();
    assertThat(members2.items().size()).isEqualTo(2);
    assertThat(members2.items())
        .map(RoleUser::getUsername)
        .contains("user0@email.com", "user1@email.com");
  }

  @Test
  public void canMigratePermissions() throws URISyntaxException, IOException, InterruptedException {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations =
                  client.newAuthorizationSearchRequest().send().join().items();
              final var migratedAuthorizations =
                  authorizations.stream()
                      .map(Authorization::getOwnerId)
                      .filter(ROLE_IDS::contains)
                      .toList();
              assertThat(migratedAuthorizations.size()).isEqualTo(ROLE_PERMISSIONS.size());
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final SearchResponse<Authorization> newResponse =
        client.newAuthorizationSearchRequest().send().join();
    assertThat(newResponse.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            // Role permissions
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
                    PermissionType.CANCEL_PROCESS_INSTANCE,
                    PermissionType.MODIFY_PROCESS_INSTANCE,
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
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
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
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
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
                Set.of(PermissionType.READ)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.READ)));
  }

  @Test
  public void canMigrateAuthorizations()
      throws URISyntaxException, IOException, InterruptedException {
    // when
    createAuthorizations();

    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final SearchResponse<Authorization> authorizations =
                  client.newAuthorizationSearchRequest().send().join();
              final var migratedAuthorizations =
                  authorizations.items().stream()
                      .map(Authorization::getOwnerId)
                      .filter(id -> List.of("user0@email.com", "user1@email.com").contains(id))
                      .toList();
              assertThat(migratedAuthorizations.size()).isEqualTo(2);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final SearchResponse<Authorization> response =
        client.newAuthorizationSearchRequest().send().join();
    assertThat(response.items())
        .map(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "user0@email.com",
                OwnerType.USER,
                "my-test-resource",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                "user1@email.com",
                OwnerType.USER,
                "another-test-resource",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)));
  }

  @Test
  public void canMigrateClients() throws IOException, URISyntaxException, InterruptedException {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations =
                  client.newAuthorizationSearchRequest().send().join().items().stream()
                      .map(Authorization::getOwnerType)
                      .filter(OwnerType.CLIENT::equals)
                      .toList();
              assertThat(authorizations.size()).isEqualTo(9);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.MESSAGE,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.SYSTEM,
                Set.of(PermissionType.UPDATE, PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.UPDATE, PermissionType.DELETE, PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.BATCH,
                Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
            tuple(
                "tasklist-client",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(PermissionType.READ)),
            tuple(
                "tasklist-client",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK)));
  }

  private void createAuthorizations() throws IOException, InterruptedException, URISyntaxException {
    final HttpRequest request1 =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(externalIdentityUrl(IDENTITY), "/api/authorizations")))
            .PUT(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                       "entityId": "user0",
                       "entityType": "USER",
                       "resourceKey": "my-test-resource",
                       "resourceType": "process-definition",
                       "permissions":[
                          "READ",
                          "DELETE",
                          "UPDATE_PROCESS_INSTANCE",
                          "DELETE_PROCESS_INSTANCE",
                          "START_PROCESS_INSTANCE"
                       ],
                       "organizationId": "org123"
                    }
                    """))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer %s".formatted(TOKEN))
            .build();

    final HttpRequest request2 =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(externalIdentityUrl(IDENTITY), "/api/authorizations")))
            .PUT(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                       "entityId": "user1",
                       "entityType": "USER",
                       "resourceKey": "another-test-resource",
                       "resourceType": "decision-definition",
                       "permissions":[
                          "READ",
                          "DELETE"
                       ],
                       "organizationId": "org123"
                    }
                    """))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer %s".formatted(TOKEN))
            .build();

    httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
    httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
  }

  private void createGroups() throws IOException, InterruptedException, URISyntaxException {
    final var groupsNames = List.of("groupA", "groupB", "groupC");
    for (final String groupName : groupsNames) {
      createGroup(groupName);
    }
  }

  private void createGroup(final String groupName)
      throws IOException, InterruptedException, URISyntaxException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(externalIdentityUrl(IDENTITY), "/api/groups")))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "name": "%s",
                      "organizationId": "org123"
                    }
                    """
                        .formatted(groupName)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer %s".formatted(TOKEN))
            .build();

    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private void assignGroupsToUsers() throws IOException, URISyntaxException, InterruptedException {
    final var groupIds =
        getGroups().stream().map(io.camunda.migration.identity.dto.Group::id).toList();
    assignGroupToUser(groupIds.getFirst(), "user0");
    assignGroupToUser(groupIds.get(1), "user0");
    assignGroupToUser(groupIds.getLast(), "user1");
  }

  private void assignGroupToUser(final String groupId, final String userId)
      throws IOException, InterruptedException, URISyntaxException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            externalIdentityUrl(IDENTITY),
                            "/api/groups/%s/users".formatted(groupId))))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "userId": "%s",
                      "organizationId": "org123"
                    }
                    """
                        .formatted(userId)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer %s".formatted(TOKEN))
            .build();

    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private List<io.camunda.migration.identity.dto.Group> getGroups()
      throws IOException, InterruptedException, URISyntaxException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            externalIdentityUrl(IDENTITY), "/api/groups?organizationId=org123")))
            .GET()
            .header("Authorization", "Bearer %s".formatted(TOKEN))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
  }

  private static void stubConsoleClient() {
    // IDENTITY
    stubFor(
        get("/.well-known/jwks.json")
            .willReturn(
                ok().withHeader("Content-Type", "application/json")
                    .withBody(loadFixture("jwks.json"))));

    // console auth token
    stubFor(
        post(urlEqualTo("/oauth/token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                WireMock.equalToJson(
                    AUTH_TOKEN_BODY.formatted(
                        CONSOLE_CLIENT_ID, CONSOLE_CLIENT_SECRET, CONSOLE_AUDIENCE)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\": \"" + TOKEN + "\"}")));

    // management identity auth token
    stubFor(
        post(urlEqualTo("/oauth/token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                WireMock.equalToJson(
                    AUTH_TOKEN_BODY.formatted(
                        IDENTITY_CLIENT, IDENTITY_CLIENT_SECRET, CAMUNDA_IDENTITY_RESOURCE_SERVER)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\": \"" + TOKEN + "\"}")));

    // CONSOLE
    final String endpoint =
        MessageFormat.format(
            "/external/organizations/{0}/clusters/{1}/migrationData/{2}",
            "org123", "cluster123", "client123");

    final String responseJson =
        """
        {
          "members": [
            {
              "userId": "user0",
              "roles": ["owner", "developer"],
              "email": "user0@email.com",
              "name": "John Doe"
            },
            {
              "userId": "user1",
              "roles": ["owner", "developer"],
              "email": "user1@email.com",
              "name": "John Doe"
            }
          ],
          "clients": [
            {
              "name": "console-client",
              "clientId": "client123",
              "permissions": ["Operate", "Zeebe"]
            },
            {
              "name": "Tasklist",
              "clientId": "tasklist-client",
              "permissions": ["Tasklist"]
             }
          ]
        }
        """;

    stubFor(
        get(urlEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));
  }

  protected static String loadFixture(final String filename) {
    try (final InputStream inputStream =
        SaaSIdentityMigrationIT.class
            .getClassLoader()
            .getResourceAsStream("identity-migration/" + filename)) {
      return new BufferedReader(new InputStreamReader(inputStream))
          .lines()
          .collect(Collectors.joining("\n"));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
