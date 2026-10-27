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
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies OIDC authentication isolation across physical tenants over gRPC: a token issued by one
 * tenant's IdP (distinct issuer) is accepted on that tenant and rejected on another whose OIDC
 * configuration points at a different issuer.
 *
 * <p>Tests use {@code newTopologyRequest()} rather than a PT-scoped data operation because topology
 * is served by the gateway layer without partition involvement, making it reliable in this
 * lightweight OIDC setup. It hits the same gRPC auth interceptor as any other call, so it proves
 * whether the token is accepted or rejected based on issuer match. {@code Storage.none()} is
 * intentional: OIDC credentials live in Keycloak, not in a per-PT user store.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantGrpcOidcAuthIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_A = "realm-a";
  private static final String REALM_B = "realm-b";
  private static final String CLIENT_ID_A = "client-a";
  private static final String CLIENT_SECRET_A = "secret-a";
  private static final String CLIENT_ID_B = "client-b";
  private static final String CLIENT_SECRET_B = "secret-b";
  private static final String AUDIENCE = "zeebe";

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
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  // PT-A token targeting PT-A (accepted) and PT-B (rejected — wrong issuer)
  private static CamundaClient tenantAClient;
  private static CamundaClient tenantAOnTenantBClient;

  // PT-B token targeting PT-B (accepted) and PT-A (rejected — wrong issuer)
  private static CamundaClient tenantBClient;
  private static CamundaClient tenantBOnTenantAClient;

  @BeforeAll
  static void startBroker(@TempDir final Path tempDir) {
    configureRealm(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A);
    configureRealm(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B);

    final String keycloakBase = KEYCLOAK.getAuthServerUrl();
    // withSecurityConfig sets the broker-wide OIDC issuer used by the default physical tenant;
    // non-default PTs override it via configureTenantOidc below.
    // redirectUri must be set because Spring's OAuth2 client registration requires it as a
    // mandatory field even in resource-server (gRPC) mode where no browser redirect ever occurs.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(keycloakBase + "/realms/" + REALM_A);
          c.getAuthentication().getOidc().setClientId(CLIENT_ID_A);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // PT-A: same realm as root; PT-B: distinct realm so its issuer differs from PT-A.
    configureTenantOidc(TENANT_A, keycloakBase + "/realms/" + REALM_A, CLIENT_ID_A);
    configureTenantOidc(TENANT_B, keycloakBase + "/realms/" + REALM_B, CLIENT_ID_B);

    BROKER.start();

    tenantAClient = oidcClient(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A, TENANT_A, tempDir);
    tenantAOnTenantBClient = oidcClient(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A, TENANT_B, tempDir);
    tenantBClient = oidcClient(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B, TENANT_B, tempDir);
    tenantBOnTenantAClient = oidcClient(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B, TENANT_A, tempDir);

    Awaitility.await("broker ready")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> tenantAClient.newTopologyRequest().send().join());
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(
        tenantAClient, tenantAOnTenantBClient, tenantBClient, tenantBOnTenantAClient);
  }

  @Test
  void shouldAuthenticateTenantAOidcTokenOnlyInTenantA() {
    // given — a token issued by PT-A's IdP (realm-a)
    // when / then — accepted on PT-A whose OIDC config points at the same issuer
    assertThatNoException()
        .as("PT-A token is accepted on PT-A — issuer matches")
        .isThrownBy(() -> tenantAClient.newTopologyRequest().send().join());

    // when / then — rejected on PT-B whose OIDC config points at a different issuer
    assertThatThrownBy(() -> tenantAOnTenantBClient.newTopologyRequest().send().join())
        .as("PT-A token is rejected on PT-B — issuer mismatch")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  @Test
  void shouldAuthenticateTenantBOidcTokenOnlyInTenantB() {
    // given — a token issued by PT-B's IdP (realm-b)
    // when / then — accepted on PT-B whose OIDC config points at the same issuer
    assertThatNoException()
        .as("PT-B token is accepted on PT-B — issuer matches")
        .isThrownBy(() -> tenantBClient.newTopologyRequest().send().join());

    // when / then — rejected on PT-A whose OIDC config points at a different issuer
    assertThatThrownBy(() -> tenantBOnTenantAClient.newTopologyRequest().send().join())
        .as("PT-B token is rejected on PT-A — issuer mismatch")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  private static void configureTenantOidc(
      final String tenantId, final String issuerUri, final String clientId) {
    BROKER.withPtConfig(
        tenantId,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri);
          oidc.setClientId(clientId);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // providers.assigned = ["oidc"] selects the per-PT default slot (authentication.oidc.*) as the
    // active provider for this PT; required for all non-default PTs under OIDC authentication.
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static void configureRealm(
      final String realmName, final String clientId, final String clientSecret) {
    final var clientRepresentation = new ClientRepresentation();
    clientRepresentation.setClientId(clientId);
    clientRepresentation.setEnabled(true);
    clientRepresentation.setClientAuthenticatorType("client-secret");
    clientRepresentation.setSecret(clientSecret);
    clientRepresentation.setServiceAccountsEnabled(true);

    final var realmRepresentation = new RealmRepresentation();
    realmRepresentation.setRealm(realmName);
    realmRepresentation.setEnabled(true);
    realmRepresentation.setClients(List.of(clientRepresentation));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realmRepresentation);
    }
  }

  private static CamundaClient oidcClient(
      final String realm,
      final String clientId,
      final String clientSecret,
      final String targetTenantId,
      final Path credentialsCacheDir) {
    return TENANTS
        .newClientBuilder(BROKER, targetTenantId)
        .preferRestOverGrpc(false)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .audience(AUDIENCE)
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(
                    credentialsCacheDir.resolve(realm + "-on-" + targetTenantId).toString())
                .build())
        .build();
  }
}
