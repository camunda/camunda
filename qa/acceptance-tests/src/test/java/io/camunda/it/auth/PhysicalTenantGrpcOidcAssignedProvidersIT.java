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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that gRPC honors {@code providers.assigned}: a physical tenant only trusts the OIDC
 * providers assigned to it, and resolves a token with the claim settings of the provider that
 * issued it. The root declares a default slot (realm-k) and a named provider {@code okta}
 * (realm-o); {@code tenanta} is assigned only {@code okta}, {@code tenantb} only the default slot.
 *
 * <p>{@code okta} maps the client id from a custom claim, and only that claim's value is an admin
 * of {@code tenanta}. Every token carries {@code sub}, so a principal resolved with any other claim
 * settings is still authenticated but not authorized: the deployment is what proves the claims.
 *
 * <p>The root default slot also sets a groups claim and UserInfo augmentation. {@code tenanta}
 * keeps both settings although it does not assign the default slot, which must not make the root
 * issuer trusted again. {@code okta} sets no groups claim of its own, so its tokens are read with
 * the inherited root one.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantGrpcOidcAssignedProvidersIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_K = "realm-k";
  private static final String REALM_O = "realm-o";
  private static final String CLIENT_K = "client-k";
  private static final String CLIENT_O = "client-o";
  private static final String OKTA_CLIENT_ID_CLAIM = "okta_client";
  private static final String OKTA_PRINCIPAL = "okta-principal";
  private static final String CLIENT_O_MEMBER = "client-o-member";
  private static final String OKTA_GROUP = "okta-team";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC)
              .withAuthorizationsEnabled());

  private static CamundaClient keycloakOnTenantA;
  private static CamundaClient oktaOnTenantA;
  private static CamundaClient keycloakOnTenantB;
  private static CamundaClient oktaOnTenantB;
  private static CamundaClient oktaMemberOnTenantA;

  @BeforeAll
  static void startBroker(@TempDir final Path tempDir) {
    createRealm(REALM_K, serviceClient(CLIENT_K));
    createRealm(
        REALM_O,
        serviceClient(CLIENT_O, hardcodedClaim(OKTA_CLIENT_ID_CLAIM, OKTA_PRINCIPAL, "String")),
        serviceClient(
            CLIENT_O_MEMBER,
            hardcodedClaim(OKTA_CLIENT_ID_CLAIM, "okta-member", "String"),
            hardcodedClaim("groups", "[\"" + OKTA_GROUP + "\"]", "JSON")));

    final String keycloakBase = KEYCLOAK.getAuthServerUrl();
    // redirectUri is required by Spring's client registration even though gRPC never redirects
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(keycloakBase + "/realms/" + REALM_K);
          c.getAuthentication().getOidc().setClientId(CLIENT_K);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
          c.getAuthentication().getOidc().setGroupsClaim("groups");
          c.getAuthentication().getOidc().getUserInfoAugmentation().setEnabled(true);
        });
    final String okta = "camunda.security.authentication.providers.oidc.okta.";
    BROKER
        .withProperty(okta + "issuer-uri", keycloakBase + "/realms/" + REALM_O)
        .withProperty(okta + "client-id", CLIENT_O)
        .withProperty(okta + "client-id-claim", OKTA_CLIENT_ID_CLAIM)
        .withProperty(okta + "redirect-uri", "{baseUrl}/login/oauth2/code/okta");
    assign(TENANT_A, "okta");
    assign(TENANT_B, "oidc");
    BROKER.withPtConfig(
        TENANT_A,
        c ->
            c.getSecurity()
                .getInitialization()
                .setDefaultRoles(
                    Map.of(
                        "admin",
                        Map.of(
                            "users",
                            List.of(TENANTS.adminUsername(TENANT_A)),
                            "clients",
                            List.of(OKTA_PRINCIPAL),
                            "groups",
                            List.of(OKTA_GROUP)))));

    BROKER.start();

    keycloakOnTenantA = client(REALM_K, CLIENT_K, TENANT_A, tempDir);
    oktaOnTenantA = client(REALM_O, CLIENT_O, TENANT_A, tempDir);
    keycloakOnTenantB = client(REALM_K, CLIENT_K, TENANT_B, tempDir);
    oktaOnTenantB = client(REALM_O, CLIENT_O, TENANT_B, tempDir);
    oktaMemberOnTenantA = client(REALM_O, CLIENT_O_MEMBER, TENANT_A, tempDir);

    Awaitility.await("broker ready")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(() -> keycloakOnTenantB.newTopologyRequest().send().join());
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(
        keycloakOnTenantA, oktaOnTenantA, keycloakOnTenantB, oktaOnTenantB, oktaMemberOnTenantA);
  }

  @Test
  void shouldAcceptTokenOfAssignedProvider() {
    // when / then
    assertThatNoException()
        .as("okta token is accepted on tenanta")
        .isThrownBy(() -> oktaOnTenantA.newTopologyRequest().send().join());
    assertThatNoException()
        .as("default-slot token is accepted on tenantb")
        .isThrownBy(() -> keycloakOnTenantB.newTopologyRequest().send().join());
  }

  @Test
  void shouldResolveTokenWithClaimSettingsOfAssignedProvider() {
    // given
    final var process = Bpmn.createExecutableProcess("okta_process").startEvent().endEvent().done();

    // when / then
    assertThatNoException()
        .as("okta token deploys on tenanta, so it resolved to the admin %s", OKTA_PRINCIPAL)
        .isThrownBy(() -> deploy(oktaOnTenantA, process, "okta_process"));
  }

  @Test
  void shouldForwardGroupsOfAssignedProviderToBroker() {
    // given a client that is not an admin itself, only through its okta group
    final var process =
        Bpmn.createExecutableProcess("okta_group_process").startEvent().endEvent().done();

    // when / then
    assertThatNoException()
        .as(
            "okta group member deploys on tenanta, so its groups were read with the inherited claim")
        .isThrownBy(() -> deploy(oktaMemberOnTenantA, process, "okta_group_process"));
  }

  @Test
  void shouldRejectTokenOfProviderNotAssignedToTenant() {
    // when / then
    assertUnauthenticated(keycloakOnTenantA, "default-slot token on tenanta (assigned only okta)");
    assertUnauthenticated(oktaOnTenantB, "okta token on tenantb (assigned only the default slot)");
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

  private static void assertUnauthenticated(final CamundaClient client, final String description) {
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .as(description)
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  private static void assign(final String tenantId, final String providerId) {
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        providerId);
  }

  private static void createRealm(final String realmName, final ClientRepresentation... clients) {
    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(List.of(clients));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ClientRepresentation serviceClient(
      final String clientId, final ProtocolMapperRepresentation... mappers) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(clientId + "-secret");
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(List.of(mappers));
    return client;
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String name, final String value, final String jsonType) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(name + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", name,
            "claim.value", value,
            "jsonType.label", jsonType,
            "access.token.claim", "true"));
    return mapper;
  }

  private static CamundaClient client(
      final String realm, final String clientId, final String tenantId, final Path cacheDir) {
    return TENANTS
        .newClientBuilder(BROKER, tenantId)
        .preferRestOverGrpc(false)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientId + "-secret")
                .audience("zeebe")
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(cacheDir.resolve(realm + "-on-" + tenantId).toString())
                .build())
        .build();
  }
}
