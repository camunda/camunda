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
 * Verifies OIDC claim-mapping isolation across physical tenants over gRPC: both PTs trust the SAME
 * issuer (one shared Keycloak realm), so the issuer cannot be the discriminator. Instead, each PT
 * maps the caller identity from its OWN distinct custom claim, used as both {@code usernameClaim}
 * and {@code clientIdClaim}, and each OIDC client's access token carries a distinct custom claim
 * (injected via a Keycloak hardcoded-claim protocol mapper). A token is accepted only on the PT
 * whose configured claim it actually carries — on the other PT, neither claim resolves, so
 * authentication is rejected (UNAUTHENTICATED) — proving that per-PT claim mapping, not just issuer
 * matching, is applied.
 *
 * <p>Tests use {@code newTopologyRequest()} rather than a PT-scoped data operation because topology
 * is served by the gateway layer without partition involvement, making it reliable in this
 * lightweight OIDC setup. It hits the same gRPC auth interceptor as any other call, so it proves
 * whether the token is accepted or rejected based on the mapped client id. {@code Storage.none()}
 * is intentional: OIDC credentials live in Keycloak, not in a per-PT user store.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantGrpcOidcClaimMappingIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_SHARED = "realm-shared";
  private static final String CLIENT_ID_A = "client-a";
  private static final String CLIENT_SECRET_A = "secret-a";
  private static final String CLIENT_ID_B = "client-b";
  private static final String CLIENT_SECRET_B = "secret-b";
  private static final String CLAIM_A = "client-a-id";
  private static final String CLAIM_B = "client-b-id";
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

  // client-a token targeting PT-A (accepted) and PT-B (rejected — claim not mapped there)
  private static CamundaClient clientAOnA;
  private static CamundaClient clientAOnB;

  // client-b token targeting PT-B (accepted) and PT-A (rejected — claim not mapped there)
  private static CamundaClient clientBOnB;
  private static CamundaClient clientBOnA;

  @BeforeAll
  static void startBroker(@TempDir final Path tempDir) {
    configureRealm();

    final String issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM_SHARED;
    // withSecurityConfig sets the broker-wide OIDC issuer used by the default physical tenant;
    // non-default PTs override it (to the same issuer) plus their claim mapping below.
    // redirectUri must be set because Spring's OAuth2 client registration requires it as a
    // mandatory field even in resource-server (gRPC) mode where no browser redirect ever occurs.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(issuerUri);
          c.getAuthentication().getOidc().setClientId(CLIENT_ID_A);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // Both PTs share the SAME issuer; only the claim mapping differs, isolating claim mapping
    // from issuer isolation.
    configureTenantOidc(TENANT_A, issuerUri, CLAIM_A);
    configureTenantOidc(TENANT_B, issuerUri, CLAIM_B);

    BROKER.start();

    clientAOnA = oidcClient(CLIENT_ID_A, CLIENT_SECRET_A, TENANT_A, tempDir);
    clientAOnB = oidcClient(CLIENT_ID_A, CLIENT_SECRET_A, TENANT_B, tempDir);
    clientBOnB = oidcClient(CLIENT_ID_B, CLIENT_SECRET_B, TENANT_B, tempDir);
    clientBOnA = oidcClient(CLIENT_ID_B, CLIENT_SECRET_B, TENANT_A, tempDir);

    Awaitility.await("broker ready")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> clientAOnA.newTopologyRequest().send().join());
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(clientAOnA, clientAOnB, clientBOnB, clientBOnA);
  }

  @Test
  void shouldAuthenticateClientAClaimOnlyInTenantA() {
    // given — a client-a token carrying the "client-a-id" claim
    // when / then — accepted on PT-A whose clientIdClaim is "client-a-id"
    assertThatNoException()
        .as("client-a token is accepted on PT-A — clientIdClaim matches")
        .isThrownBy(() -> clientAOnA.newTopologyRequest().send().join());

    // when / then — rejected on PT-B whose clientIdClaim is "client-b-id" (not present on token)
    assertThatThrownBy(() -> clientAOnB.newTopologyRequest().send().join())
        .as("client-a token is rejected on PT-B — clientIdClaim not present on token")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  @Test
  void shouldAuthenticateClientBClaimOnlyInTenantB() {
    // given — a client-b token carrying the "client-b-id" claim
    // when / then — accepted on PT-B whose clientIdClaim is "client-b-id"
    assertThatNoException()
        .as("client-b token is accepted on PT-B — clientIdClaim matches")
        .isThrownBy(() -> clientBOnB.newTopologyRequest().send().join());

    // when / then — rejected on PT-A whose clientIdClaim is "client-a-id" (not present on token)
    assertThatThrownBy(() -> clientBOnA.newTopologyRequest().send().join())
        .as("client-b token is rejected on PT-A — clientIdClaim not present on token")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED));
  }

  private static void configureTenantOidc(
      final String tenantId, final String issuerUri, final String claimName) {
    BROKER.withPtConfig(
        tenantId,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri);
          // Both usernameClaim and clientIdClaim point at this PT's own custom claim: on a token
          // that doesn't carry it, neither resolves, so authentication is rejected outright.
          oidc.setUsernameClaim(claimName);
          oidc.setClientIdClaim(claimName);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // providers.assigned = ["oidc"] selects the per-PT default slot (authentication.oidc.*) as the
    // active provider for this PT; required for all non-default PTs under OIDC authentication.
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static void configureRealm() {
    final var clientA = clientWithHardcodedClaim(CLIENT_ID_A, CLIENT_SECRET_A, CLAIM_A);
    final var clientB = clientWithHardcodedClaim(CLIENT_ID_B, CLIENT_SECRET_B, CLAIM_B);

    final var realmRepresentation = new RealmRepresentation();
    realmRepresentation.setRealm(REALM_SHARED);
    realmRepresentation.setEnabled(true);
    realmRepresentation.setClients(List.of(clientA, clientB));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realmRepresentation);
    }
  }

  private static ClientRepresentation clientWithHardcodedClaim(
      final String clientId, final String clientSecret, final String claimName) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(claimName + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", claimName,
            "claim.value", clientId,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "true"));

    final var clientRepresentation = new ClientRepresentation();
    clientRepresentation.setClientId(clientId);
    clientRepresentation.setEnabled(true);
    clientRepresentation.setClientAuthenticatorType("client-secret");
    clientRepresentation.setSecret(clientSecret);
    clientRepresentation.setServiceAccountsEnabled(true);
    clientRepresentation.setProtocolMappers(List.of(mapper));
    return clientRepresentation;
  }

  private static CamundaClient oidcClient(
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
                        + REALM_SHARED
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(
                    credentialsCacheDir.resolve(clientId + "-on-" + targetTenantId).toString())
                .build())
        .build();
  }
}
