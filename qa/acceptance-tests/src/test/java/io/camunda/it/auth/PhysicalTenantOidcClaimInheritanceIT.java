/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression test for scenario 5 of camunda/camunda#65633, over REST and gRPC: {@code tenanta}
 * overrides the {@code client-id-claim} of its default slot, and is assigned only the named
 * provider {@code okta}, which sets no claims. The tenant does not assign the default slot, so the
 * narrowing to {@code providers.assigned} must keep the tenant's claim for {@code okta} to inherit.
 *
 * <p>One M2M token carries two client-id claims with different values. It is admin on {@code
 * tenanta} only by the tenant's claim, and on the default tenant only by the global claim. So a
 * deployment succeeds only when each tenant reads the token with its own claim.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantOidcClaimInheritanceIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String REALM = "realm-inherit-okta";
  private static final String M2M_CLIENT = "okta-app";
  private static final String GLOBAL_CLAIM = "root_client";
  private static final String TENANT_CLAIM = "tenant_client";
  private static final String GLOBAL_PRINCIPAL = "root-view";
  private static final String TENANT_PRINCIPAL = "tenant-view";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("inherit-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("inherit-tenanta"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC)
              .withAuthorizationsEnabled());

  private static Path tempDir;

  @BeforeAll
  static void startBroker(@TempDir final Path dir) {
    tempDir = dir;
    createRealm();

    // the global claim, and a named provider without claims
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setClientIdClaim(GLOBAL_CLAIM);
          c.getInitialization().setMappingRules(List.of());
          c.getInitialization()
              .setDefaultRoles(
                  Map.of(
                      "admin", Map.of("users", List.of(), "clients", List.of(GLOBAL_PRINCIPAL))));
        });
    final var okta = "camunda.security.authentication.providers.oidc.okta.";
    BROKER
        .withProperty(okta + "issuer-uri", KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM)
        .withProperty(okta + "client-id", "okta-login")
        .withProperty(okta + "redirect-uri", "{baseUrl}/sso-callback");
    assign(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    assign(TENANT_A);
    // the tenant overrides the claim of its default slot, which it does not assign
    BROKER.withPtConfig(
        TENANT_A,
        c -> {
          c.getSecurity().getAuthentication().getOidc().setClientIdClaim(TENANT_CLAIM);
          c.getSecurity()
              .getInitialization()
              .setDefaultRoles(
                  Map.of(
                      "admin",
                      Map.of(
                          "users",
                          List.of(TENANTS.adminUsername(TENANT_A)),
                          "clients",
                          List.of(TENANT_PRINCIPAL))));
        });

    BROKER.start();
  }

  @ParameterizedTest(name = "over REST = {0}")
  @ValueSource(booleans = {true, false})
  void shouldReadTheTokenWithTheTenantClaimOnTheTenant(final boolean rest) {
    // given a client token from the provider
    try (final var client = client(TENANT_A, rest)) {
      // when / then it may deploy only by the client-id claim it inherits
      assertDeploys(client, TENANT_A, "tenant_" + rest);
    }
  }

  @ParameterizedTest(name = "over REST = {0}")
  @ValueSource(booleans = {true, false})
  void shouldReadTheTokenWithTheGlobalClaimOnTheDefaultTenant(final boolean rest) {
    // given a client token from the provider
    try (final var client = client(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, rest)) {
      // when / then it may deploy only by the client-id claim it inherits
      assertDeploys(client, PhysicalTenantsITHelper.DEFAULT_TENANT_ID, "default_" + rest);
    }
  }

  private static void assertDeploys(
      final CamundaClient client, final String tenantId, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    assertThatNoException()
        .as("the M2M token is admin on %s only by that tenant's client-id claim", tenantId)
        .isThrownBy(() -> deploy(client, process, processId));
  }

  /** Deploys {@code process}, retrying while the tenant's partition still elects a leader. */
  private static void deploy(
      final CamundaClient client, final BpmnModelInstance process, final String processId) {
    Awaitility.await("deployment of '%s' succeeds".formatted(processId))
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  private static void assign(final String tenantId) {
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "okta");
  }

  private static CamundaClient client(final String tenantId, final boolean rest) {
    return TENANTS
        .newClientBuilder(BROKER, tenantId)
        .preferRestOverGrpc(rest)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(M2M_CLIENT)
                .clientSecret(M2M_CLIENT + "-secret")
                .audience("zeebe")
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + REALM
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(tempDir.resolve(tenantId + "-" + rest).toString())
                .build())
        .build();
  }

  /** One service client whose token carries the global and the tenant client-id claim. */
  private static void createRealm() {
    final var client = new ClientRepresentation();
    client.setClientId(M2M_CLIENT);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(M2M_CLIENT + "-secret");
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(
        List.of(
            hardcodedClaim(GLOBAL_CLAIM, GLOBAL_PRINCIPAL),
            hardcodedClaim(TENANT_CLAIM, TENANT_PRINCIPAL)));

    final var realm = new RealmRepresentation();
    realm.setRealm(REALM);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String name, final String value) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(name + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name",
            name,
            "claim.value",
            value,
            "jsonType.label",
            "String",
            "access.token.claim",
            "true"));
    return mapper;
  }
}
