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
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.Tenant;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers(parallel = true)
public class OidcIdentityMigrationIT {

  @TestZeebe(autoStart = false)
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAdditionalProfile("identity")
          .withBasicAuth()
          .withAuthorizationsEnabled();

  @Container
  private static final ElasticsearchContainer ELASTIC = IdentityMigrationTestUtil.getElastic();

  @Container
  private static final GenericContainer<?> POSTGRES = IdentityMigrationTestUtil.getPostgres();

  @Container
  private static final KeycloakContainer KEYCLOAK = IdentityMigrationTestUtil.getKeycloak();

  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentitySMOidc(KEYCLOAK, POSTGRES)
          .withEnv("IDENTITY_TENANTS_0_NAME", "tenant 1")
          .withEnv("IDENTITY_TENANTS_0_TENANT-ID", "tenant1")
          .withEnv("IDENTITY_TENANTS_1_NAME", "tenant 2")
          .withEnv("IDENTITY_TENANTS_1_TENANT-ID", "tenant2")
          .withEnv("IDENTITY_MAPPING_RULES_1_NAME", "Rule 2")
          .withEnv("IDENTITY_MAPPING_RULES_1_CLAIM_NAME", "claim1")
          .withEnv("IDENTITY_MAPPING_RULES_1_CLAIM_VALUE", "value1")
          .withEnv("IDENTITY_MAPPING_RULES_1_OPERATOR", "EQUALS")
          .withEnv("IDENTITY_MAPPING_RULES_1_RULE_TYPE", "ROLE")
          .withEnv("IDENTITY_MAPPING_RULES_1_APPLIED_ROLE_NAMES_0", "Operate")
          .withEnv("IDENTITY_MAPPING_RULES_1_APPLIED_ROLE_NAMES_1", "Zeebe")
          .withEnv("IDENTITY_MAPPING_RULES_1_APPLIED_ROLE_NAMES_2", "Tasklist")
          .withEnv("IDENTITY_MAPPING_RULES_2_NAME", "Rule 3")
          .withEnv("IDENTITY_MAPPING_RULES_2_CLAIM_NAME", "claim2")
          .withEnv("IDENTITY_MAPPING_RULES_2_CLAIM_VALUE", "value2")
          .withEnv("IDENTITY_MAPPING_RULES_2_OPERATOR", "EQUALS")
          .withEnv("IDENTITY_MAPPING_RULES_2_RULE_TYPE", "TENANT")
          .withEnv("IDENTITY_MAPPING_RULES_2_APPLIED_TENANT_IDS_0", "tenant1")
          .withEnv("IDENTITY_MAPPING_RULES_2_APPLIED_TENANT_IDS_1", "tenant2")
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
    final var client = createClientRepresentation();
    final var realm = createRealmRepresentation(client);

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }

    IDENTITY.start();
    BROKER.withCamundaExporter("http://" + ELASTIC.getHttpHostAddress()).start();
  }

  @BeforeEach
  public void setup() throws Exception {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setMode(Mode.OIDC);
    migrationProperties.getManagementIdentity().setBaseUrl(externalIdentityUrl(IDENTITY));
    migrationProperties
        .getManagementIdentity()
        .setIssuerBackendUrl(externalKeycloakUrl(KEYCLOAK) + "/realms/oidc-camunda-platform/");
    migrationProperties.getManagementIdentity().setIssuerType("KEYCLOAK");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);
    migrationProperties
        .getCluster()
        .setInitialContactPoints(List.of("localhost:" + BROKER.mappedPort(CLUSTER)));
    migrationProperties.getOidc().getAudience().setIdentity(CAMUNDA_IDENTITY_RESOURCE_SERVER);
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
  public void canMigrateTenants() {
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
  }

  @Test
  public void canMigrateMappingRules()
      throws URISyntaxException, IOException, InterruptedException {
    // when
    migration.start();

    final var restAddress = client.getConfiguration().getRestAddress().toString();
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var mappingRules = searchMappingRules(restAddress);
              assertThat(mappingRules.items())
                  .extracting(MappingRuleResponse::mappingRuleId)
                  .contains("rule_2", "rule_3");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var mappingRules = searchMappingRules(restAddress).items();
    assertThat(mappingRules)
        .extracting(
            MappingRuleResponse::mappingRuleId,
            MappingRuleResponse::name,
            MappingRuleResponse::claimName,
            MappingRuleResponse::claimValue)
        .contains(
            tuple("rule_2", "Rule 2", "claim1", "value1"),
            tuple("rule_3", "Rule 3", "claim2", "value2"));

    final var mappingRuleByRole =
        client.newMappingRulesByRoleSearchRequest("operate").send().join();
    assertThat(mappingRuleByRole.items())
        .extracting(MappingRule::getMappingRuleId)
        .contains("rule_2");
    final var mappingRuleByRole2 = client.newMappingRulesByRoleSearchRequest("zeebe").send().join();
    assertThat(mappingRuleByRole2.items())
        .extracting(MappingRule::getMappingRuleId)
        .contains("rule_2");
    final var mappingRuleByRole3 =
        client.newMappingRulesByRoleSearchRequest("tasklist").send().join();
    assertThat(mappingRuleByRole3.items())
        .extracting(MappingRule::getMappingRuleId)
        .contains("rule_2");

    final var mappingRuleByTenant = searchMappingRulesInTenant(restAddress, "tenant1");
    assertThat(mappingRuleByTenant.items())
        .extracting(MappingRuleResponse::mappingRuleId)
        .contains("rule_3");
    final var mappingRuleByTenant2 = searchMappingRulesInTenant(restAddress, "tenant2");
    assertThat(mappingRuleByTenant2.items())
        .extracting(MappingRuleResponse::mappingRuleId)
        .contains("rule_3");
  }

  private static ClientRepresentation createClientRepresentation() {
    final var client = new ClientRepresentation();
    client.setClientId(IDENTITY_CLIENT);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(IDENTITY_CLIENT_SECRET);
    client.setServiceAccountsEnabled(true);
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName("test");
    mapper.setConfig(
        Map.of(
            "id.token.claim",
            "false",
            "lightweight.claim",
            "false",
            "access.token.claim",
            "true",
            "introspection.token.claim",
            "true",
            "included.custom.audience",
            "camunda-identity-resource-server"));
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-audience-mapper");
    mapper.setId(UUID.randomUUID().toString());
    client.setProtocolMappers(List.of(mapper));
    return client;
  }

  private static RealmRepresentation createRealmRepresentation(final ClientRepresentation client) {
    final var realm = new RealmRepresentation();
    realm.setRealm("oidc-camunda-platform");
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    return realm;
  }

  private MappingRulesResponse searchMappingRules(final String restAddress)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted("demo", "demo").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/mapping-rules/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), MappingRulesResponse.class);
  }

  private MappingRulesResponse searchMappingRulesInTenant(
      final String restAddress, final String tenantId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted("demo", "demo").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            restAddress, "v2/tenants/" + tenantId + "/mapping-rules/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), MappingRulesResponse.class);
  }

  private record MappingRulesResponse(List<MappingRuleResponse> items) {}

  private record MappingRuleResponse(
      String claimName, String claimValue, String name, String mappingRuleId) {}
}
