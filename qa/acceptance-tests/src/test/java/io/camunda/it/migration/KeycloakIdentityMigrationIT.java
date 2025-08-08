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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
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
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
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
public class KeycloakIdentityMigrationIT {

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

  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentitySMKeycloak(KEYCLOAK, POSTGRES)
          // this triggers the setup of the default roles for operate, tasklist and zeebe
          .withEnv("KEYCLOAK_INIT_OPERATE_SECRET", "operate")
          .withEnv("KEYCLOAK_INIT_OPERATE_ROOT_URL", "http://localhost:8081")
          .withEnv("KEYCLOAK_INIT_TASKLIST_SECRET", "tasklist")
          .withEnv("KEYCLOAK_INIT_TASKLIST_ROOT_URL", "http://localhost:8081")
          .withEnv("KEYCLOAK_INIT_ZEEBE_NAME", "zeebe")
          // create groups
          .withEnv("KEYCLOAK_GROUPS_0_NAME", "groupA")
          .withEnv("KEYCLOAK_GROUPS_1_NAME", "groupB")
          .withEnv("KEYCLOAK_GROUPS_2_NAME", "groupC")
          // create users and assign them to groups and roles
          .withEnv("KEYCLOAK_USERS_0_EMAIL", "user0@email.com")
          .withEnv("KEYCLOAK_USERS_0_FIRST-NAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_LAST-NAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_USERNAME", "user0")
          .withEnv("KEYCLOAK_USERS_0_PASSWORD", "password")
          .withEnv("KEYCLOAK_USERS_0_GROUPS_0", "groupA")
          .withEnv("KEYCLOAK_USERS_0_GROUPS_1", "groupB")
          .withEnv("KEYCLOAK_USERS_0_ROLES_0", "Identity")
          .withEnv("KEYCLOAK_USERS_0_ROLES_1", "Operate")
          .withEnv("KEYCLOAK_USERS_0_ROLES_2", "Tasklist")
          .withEnv("KEYCLOAK_USERS_0_ROLES_3", "Zeebe")
          .withEnv("KEYCLOAK_USERS_1_EMAIL", "user1@email.com")
          .withEnv("KEYCLOAK_USERS_1_FIRST-NAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_LAST-NAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_USERNAME", "user1")
          .withEnv("KEYCLOAK_USERS_1_PASSWORD", "password")
          .withEnv("KEYCLOAK_USERS_1_GROUPS_0", "groupC")
          .withEnv("KEYCLOAK_USERS_1_ROLES_0", "Zeebe")
          // assign authorizations to groups
          .withEnv("IDENTITY_AUTHORIZATIONS_0_GROUP_NAME", "groupA")
          .withEnv("IDENTITY_AUTHORIZATIONS_0_RESOURCE_KEY", "*")
          .withEnv("IDENTITY_AUTHORIZATIONS_0_RESOURCE_TYPE", "process-definition")
          .withEnv("IDENTITY_AUTHORIZATIONS_0_PERMISSIONS_0", "READ")
          .withEnv("IDENTITY_AUTHORIZATIONS_0_PERMISSIONS_1", "DELETE")
          .withEnv("IDENTITY_AUTHORIZATIONS_1_GROUP_NAME", "groupB")
          .withEnv("IDENTITY_AUTHORIZATIONS_1_RESOURCE_KEY", "*")
          .withEnv("IDENTITY_AUTHORIZATIONS_1_RESOURCE_TYPE", "decision-definition")
          .withEnv("IDENTITY_AUTHORIZATIONS_1_PERMISSIONS_0", "READ")
          .withEnv("IDENTITY_AUTHORIZATIONS_1_PERMISSIONS_1", "DELETE")
          // assign authorizations to users
          .withEnv("IDENTITY_AUTHORIZATIONS_2_USERNAME", "user0")
          .withEnv("IDENTITY_AUTHORIZATIONS_2_RESOURCE_KEY", "*")
          .withEnv("IDENTITY_AUTHORIZATIONS_2_RESOURCE_TYPE", "process-definition")
          .withEnv("IDENTITY_AUTHORIZATIONS_2_PERMISSIONS_0", "READ")
          .withEnv("IDENTITY_AUTHORIZATIONS_2_PERMISSIONS_1", "UPDATE_PROCESS_INSTANCE")
          .withEnv("IDENTITY_AUTHORIZATIONS_3_USERNAME", "user1")
          .withEnv("IDENTITY_AUTHORIZATIONS_3_RESOURCE_KEY", "*")
          .withEnv("IDENTITY_AUTHORIZATIONS_3_RESOURCE_TYPE", "decision-definition")
          .withEnv("IDENTITY_AUTHORIZATIONS_3_PERMISSIONS_0", "READ")
          .withEnv("IDENTITY_AUTHORIZATIONS_3_PERMISSIONS_1", "DELETE")
          // tenant
          .withEnv("IDENTITY_TENANTS_0_NAME", "tenant 1")
          .withEnv("IDENTITY_TENANTS_0_TENANT-ID", "tenant1")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_0_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_0_GROUP-NAME", "groupA")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_1_TYPE", "USER")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_1_USERNAME", "user0")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_2_TYPE", "APPLICATION")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_2_APPLICATION-ID", IDENTITY_CLIENT)
          .withEnv("IDENTITY_TENANTS_1_NAME", "tenant 2")
          .withEnv("IDENTITY_TENANTS_1_TENANT-ID", "tenant2")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_0_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_0_GROUP-NAME", "groupB")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_1_TYPE", "USER")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_1_USERNAME", "user1")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_2_TYPE", "APPLICATION")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_2_APPLICATION-ID", IDENTITY_CLIENT)
          // public client to be ignored
          .withEnv("KEYCLOAK_CLIENTS_1_NAME", "console")
          .withEnv("KEYCLOAK_CLIENTS_1_ID", "console")
          .withEnv("KEYCLOAK_CLIENTS_1_ROOT_URL", "http://console.camunda.com")
          .withEnv("KEYCLOAK_CLIENTS_1_TYPE", "public")
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
  static void init() {
    BROKER.withCamundaExporter("http://" + ELASTIC.getHttpHostAddress()).start();

    IDENTITY
        .withEnv(
            "IDENTITY_AUTH_PROVIDER_ISSUER_URL",
            "http://localhost:%d/realms/camunda-platform".formatted(KEYCLOAK.getMappedPort(8080)))
        .start();
  }

  @AfterAll
  static void afterAll() {
    BROKER.stop();
    IDENTITY.stop();
  }

  @BeforeEach
  public void setup() throws Exception {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setMode(Mode.KEYCLOAK);
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY));
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
                  .extracting(Role::getRoleId)
                  .contains("operate", "tasklist", "zeebe", "identity");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var roles = client.newRolesSearchRequest().send().join();
    assertThat(roles.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(
            tuple("operate", "Operate"),
            tuple("tasklist", "Tasklist"),
            tuple("zeebe", "Zeebe"),
            tuple("identity", "Identity"));

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple("operate", ResourceType.MESSAGE, Set.of(PermissionType.READ)),
            tuple("operate", ResourceType.RESOURCE, Set.of(PermissionType.READ)),
            tuple(
                "operate",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple("operate", ResourceType.APPLICATION, Set.of(PermissionType.ACCESS)),
            tuple(
                "operate",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                "operate",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_INSTANCE)),
            tuple(
                "operate",
                ResourceType.BATCH,
                Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
            tuple(
                "zeebe",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple("zeebe", ResourceType.MESSAGE, Set.of(PermissionType.CREATE)),
            tuple(
                "zeebe",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK)),
            tuple(
                "zeebe",
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.CREATE,
                    PermissionType.DELETE_RESOURCE)),
            tuple("zeebe", ResourceType.SYSTEM, Set.of(PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                "zeebe",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                "tasklist",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.READ_PROCESS_DEFINITION)),
            tuple("tasklist", ResourceType.APPLICATION, Set.of(PermissionType.ACCESS)),
            tuple("tasklist", ResourceType.RESOURCE, Set.of(PermissionType.READ)),
            tuple("tasklist", ResourceType.RESOURCE, Set.of(PermissionType.READ)),
            tuple(
                "identity",
                ResourceType.GROUP,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.TENANT,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.ROLE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple("identity", ResourceType.USER, Set.of(PermissionType.READ)),
            tuple("identity", ResourceType.APPLICATION, Set.of(PermissionType.ACCESS)));
  }

  @Test
  public void canMigrateGroups() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("groupa", "groupb");
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
                "groupa",
                OwnerType.GROUP,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                "groupb",
                OwnerType.GROUP,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)));
  }

  @Test
  public void canMigrateUsersRolesMembership() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var users = client.newUsersByRoleSearchRequest("zeebe").send().join();
              assertThat(users.items()).hasSize(2);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);
    final var zeebeUsers = client.newUsersByRoleSearchRequest("zeebe").send().join().items();
    assertThat(zeebeUsers)
        .extracting(RoleUser::getUsername)
        .containsExactlyInAnyOrder("user0@email.com", "user1@email.com");
    final var operateUsers = client.newUsersByRoleSearchRequest("operate").send().join().items();
    assertThat(operateUsers)
        .extracting(RoleUser::getUsername)
        .containsExactlyInAnyOrder("user0@email.com");
    final var tasklistUsers = client.newUsersByRoleSearchRequest("tasklist").send().join().items();
    assertThat(tasklistUsers)
        .extracting(RoleUser::getUsername)
        .containsExactlyInAnyOrder("user0@email.com");
    final var identityUsers = client.newUsersByRoleSearchRequest("identity").send().join().items();
    assertThat(identityUsers)
        .extracting(RoleUser::getUsername)
        .containsExactlyInAnyOrder("user0@email.com");
  }

  @Test
  public void canMigrateAuthorizations() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("user0@email.com", "user1@email.com");
            });

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "user0@email.com",
                OwnerType.USER,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE)),
            tuple(
                "user1@email.com",
                OwnerType.USER,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)));
  }

  @Test
  public void canMigrateClients() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("migration-app");
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
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.DELETE, PermissionType.UPDATE)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_PROCESS)),
            tuple(
                "migration-app", OwnerType.CLIENT, ResourceType.USER, Set.of(PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.GROUP,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.TENANT,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.SYSTEM,
                Set.of(PermissionType.UPDATE, PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.ROLE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.MESSAGE,
                Set.of(PermissionType.CREATE)));
  }

  @Test
  public void canMigrateTenants() throws URISyntaxException, IOException, InterruptedException {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tenants = client.newTenantsSearchRequest().send().join();
              assertThat(tenants.items())
                  .extracting(Tenant::getTenantId)
                  .contains("tenant1", "tenant2");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var tenants = client.newTenantsSearchRequest().send().join().items();
    assertThat(tenants)
        .extracting(Tenant::getTenantId, Tenant::getName)
        .contains(tuple("tenant1", "tenant 1"), tuple("tenant2", "tenant 2"));

    final var tenant1Users = client.newUsersByTenantSearchRequest("tenant1").send().join();
    assertThat(tenant1Users.items())
        .extracting(TenantUser::getUsername)
        .containsExactlyInAnyOrder("user0");
    final var tenant2Users = client.newUsersByTenantSearchRequest("tenant2").send().join();
    assertThat(tenant2Users.items())
        .extracting(TenantUser::getUsername)
        .containsExactlyInAnyOrder("user1");

    final var restAddress = client.getConfiguration().getRestAddress().toString();
    final var tenant1Groups = searchGroupsInTenant(restAddress, "tenant1");
    assertThat(tenant1Groups.items())
        .extracting(TenantGroup::groupId)
        .containsExactlyInAnyOrder("groupa");
    final var tenant2Groups = searchGroupsInTenant(restAddress, "tenant2");
    assertThat(tenant2Groups.items())
        .extracting(TenantGroup::groupId)
        .containsExactlyInAnyOrder("groupb");
    final var tenant1Clients = searchClientsInTenant(restAddress, "tenant1");
    assertThat(tenant1Clients.items())
        .extracting(TenantClient::clientId)
        .containsExactlyInAnyOrder(IDENTITY_CLIENT);
    final var tenant2Clients = searchClientsInTenant(restAddress, "tenant2");
    assertThat(tenant2Clients.items())
        .extracting(TenantClient::clientId)
        .containsExactlyInAnyOrder(IDENTITY_CLIENT);
  }

  private GroupsTenantResponse searchGroupsInTenant(final String restAddress, final String tenantId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted("demo", "demo").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI("%s%s".formatted(restAddress, "v2/tenants/" + tenantId + "/groups/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), GroupsTenantResponse.class);
  }

  private ClientsTenantResponse searchClientsInTenant(
      final String restAddress, final String tenantId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted("demo", "demo").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s".formatted(restAddress, "v2/tenants/" + tenantId + "/clients/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), ClientsTenantResponse.class);
  }

  private record GroupsTenantResponse(List<TenantGroup> items) {}

  private record TenantGroup(String groupId) {}

  private record ClientsTenantResponse(List<TenantClient> items) {}

  private record TenantClient(String clientId) {}
}
