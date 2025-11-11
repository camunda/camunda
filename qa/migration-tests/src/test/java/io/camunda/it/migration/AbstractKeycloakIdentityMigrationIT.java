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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
import io.camunda.migration.identity.dto.Group;
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
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers(parallel = true)
public abstract class AbstractKeycloakIdentityMigrationIT {

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
          // ensures we have no regression for https://github.com/camunda/camunda/issues/39260
          .withEnv("IDENTITY_TENANTS_1_TENANT-ID", "TenanT2")
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

  private static final GenericContainer<?> IDENTITY_88 =
      IdentityMigrationTestUtil.getManagementIdentitySMKeycloak88(KEYCLOAK, POSTGRES);

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  protected TestStandaloneIdentityMigration migration;
  protected CamundaClient client;
  protected Identity identitySdk;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

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

    identitySdk =
        new Identity(
            new IdentityConfiguration(
                migrationProperties.getManagementIdentity().getBaseUrl(),
                migrationProperties.getManagementIdentity().getIssuerBackendUrl(),
                migrationProperties.getManagementIdentity().getIssuerBackendUrl(),
                migrationProperties.getManagementIdentity().getClientId(),
                migrationProperties.getManagementIdentity().getClientSecret(),
                migrationProperties.getManagementIdentity().getAudience(),
                Type.KEYCLOAK.name()));

    client = BROKER.newClientBuilder().build();
  }

  @AfterEach
  void tearDown() {
    migration.close();
  }

  protected GroupsTenantResponse searchGroupsInTenant(
      final String restAddress, final String tenantId)
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

  protected ClientsTenantResponse searchClientsInTenant(
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

  protected void addGroupToRoleInManagementIdentity(final String groupName, final String roleName)
      throws URISyntaxException, IOException, InterruptedException {

    final String groupId = getGroupIdByName(groupName);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            externalIdentityUrl(IDENTITY_88), "/api/groups/" + groupId + "/roles")))
            .POST(
                HttpRequest.BodyPublishers.ofString("{ \"roleName\": \"%s\" }".formatted(roleName)))
            .header("Content-Type", "application/json")
            .header("Authorization", getManagementIdentityBearerToken())
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 204) {
      throw new IllegalStateException(
          "Expected 204 to be returned as status code but got " + response.statusCode());
    }
  }

  private String getGroupIdByName(final String groupName)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            externalIdentityUrl(IDENTITY_88), "/api/groups?search=" + groupName)))
            .GET()
            .header("Authorization", getManagementIdentityBearerToken())
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    final List<Group> groups = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
    return groups.stream()
        .findFirst()
        .map(Group::id)
        .orElseThrow(
            () -> new IllegalStateException("Expected to find group with name " + groupName));
  }

  private String getManagementIdentityBearerToken() {
    return "Bearer %s"
        .formatted(
            identitySdk
                .authentication()
                .requestToken(CAMUNDA_IDENTITY_RESOURCE_SERVER)
                .getAccessToken());
  }

  protected record GroupsTenantResponse(List<TenantGroup> items) {}

  protected record TenantGroup(String groupId) {}

  protected record ClientsTenantResponse(List<TenantClient> items) {}

  protected record TenantClient(String clientId) {}
}
