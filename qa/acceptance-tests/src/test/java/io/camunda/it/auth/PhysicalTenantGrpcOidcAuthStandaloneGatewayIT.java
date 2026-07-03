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
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
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
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies OIDC authentication isolation across physical tenants over gRPC via a
 * <em>standalone</em> gateway (no broker needed — OIDC token validation is self-contained in the
 * gateway):
 *
 * <ul>
 *   <li>A token issued by PT-A's IdP is accepted on PT-A and rejected on PT-B (issuer mismatch).
 *   <li>A token issued by PT-B's IdP is accepted on PT-B and rejected on PT-A.
 * </ul>
 *
 * <p>{@code newTopologyRequest()} is used as the sentinel — the standalone gateway serves topology
 * with an empty broker list (no exception), exercising the auth interceptor without requiring
 * partition involvement. {@code Storage.none()} is intentional: OIDC credentials live in Keycloak,
 * not in a per-PT user store.
 *
 * @see PhysicalTenantGrpcOidcAuthIT for the embedded-gateway counterpart
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantGrpcOidcAuthStandaloneGatewayIT {

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

  // Standalone gateway — no broker needed; OIDC token validation is self-contained in the gateway.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneGateway GATEWAY =
      new TestStandaloneGateway()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC);

  // PT-A token targeting PT-A (accepted) and PT-B (rejected — wrong issuer)
  private static CamundaClient tenantAClient;
  private static CamundaClient tenantAOnTenantBClient;

  // PT-B token targeting PT-B (accepted) and PT-A (rejected — wrong issuer)
  private static CamundaClient tenantBClient;
  private static CamundaClient tenantBOnTenantAClient;

  @BeforeAll
  static void startGateway(@TempDir final Path tempDir) {
    configureRealm(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A);
    configureRealm(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B);

    // Prevents the secondary-storage-sharing validation from rejecting startup when all PTs
    // default to the same elasticsearch endpoint.
    GATEWAY.withUnifiedConfig(
        c -> c.getData().getSecondaryStorage().setType(SecondaryStorageType.none));

    final String keycloakBase = KEYCLOAK.getAuthServerUrl();
    // Gateway-wide OIDC issuer for the default PT; non-default PTs override via
    // configureTenantOidc.
    // redirectUri is mandatory for Spring OAuth2 client registration even in resource-server mode.
    GATEWAY.withUnifiedConfig(
        c -> {
          c.getSecurity()
              .getAuthentication()
              .getOidc()
              .setIssuerUri(keycloakBase + "/realms/" + REALM_A);
          c.getSecurity().getAuthentication().getOidc().setClientId(CLIENT_ID_A);
          c.getSecurity()
              .getAuthentication()
              .getOidc()
              .setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // PT-A: same realm as root; PT-B: distinct realm so its issuer differs from PT-A.
    configureTenantOidc(TENANT_A, keycloakBase + "/realms/" + REALM_A, CLIENT_ID_A);
    configureTenantOidc(TENANT_B, keycloakBase + "/realms/" + REALM_B, CLIENT_ID_B);

    GATEWAY.start();

    tenantAClient = oidcClient(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A, TENANT_A, tempDir);
    tenantAOnTenantBClient = oidcClient(REALM_A, CLIENT_ID_A, CLIENT_SECRET_A, TENANT_B, tempDir);
    tenantBClient = oidcClient(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B, TENANT_B, tempDir);
    tenantBOnTenantAClient = oidcClient(REALM_B, CLIENT_ID_B, CLIENT_SECRET_B, TENANT_A, tempDir);

    awaitAuth(tenantAClient, "gateway ready");
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
    GATEWAY.withPtConfig(
        tenantId,
        c -> {
          c.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri);
          oidc.setClientId(clientId);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // providers.assigned = ["oidc"] selects the per-PT default slot (authentication.oidc.*) as the
    // active provider for this PT; required for all non-default PTs under OIDC authentication.
    GATEWAY.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
    // PhysicalTenantRequiredOverrideValidation requires a security.initialization.* key for every
    // explicitly-configured non-default PT. The gateway does not run identity initialization, but
    // the resolver validates config structure at startup the same way the broker does.
    GATEWAY.withPtConfig(
        tenantId,
        c ->
            c.getSecurity()
                .getInitialization()
                .setDefaultRoles(
                    Map.of("admin", Map.of("users", List.of(TENANTS.adminUsername(tenantId))))));
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
        .newClientBuilder(GATEWAY, targetTenantId)
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

  private static void awaitAuth(final CamundaClient client, final String reason) {
    Awaitility.await(reason)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> client.newTopologyRequest().send().join());
  }
}
