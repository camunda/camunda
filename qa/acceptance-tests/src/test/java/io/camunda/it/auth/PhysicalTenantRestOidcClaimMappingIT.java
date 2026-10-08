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
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.api.model.config.AuthenticationMethod;
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
 * REST counterpart of {@link PhysicalTenantGrpcOidcClaimMappingIT}, the regression test for <a
 * href="https://github.com/camunda/camunda/issues/64685">camunda/camunda#64685</a>: the REST API
 * rejected a bearer token issued for a physical tenant's own OIDC provider with {@code 401} while
 * gRPC accepted it, because the REST bearer path resolved every token with the cluster-default claim
 * configuration instead of the tenant's own.
 *
 * <p>All PTs trust the SAME issuer (one shared Keycloak realm), so the issuer alone cannot be the
 * discriminator — exactly the hard case. Each PT maps identity from its OWN distinct custom claim
 * (used as both {@code usernameClaim} and {@code clientIdClaim}), and each client's token carries a
 * distinct custom claim (injected via a Keycloak hardcoded-claim mapper). Crucially, the
 * cluster-default (root / {@code default} physical tenant) resolves identity from a custom claim
 * ({@code root-id}) that none of the per-tenant tokens carry: so before the fix, a tenant's own
 * token — resolved with the root config on the REST path — matched neither claim and was rejected
 * with {@code 401}. After the fix (per-scope bearer-token claim resolution in the
 * camunda-security-library), the REST path resolves each tenant's token with that tenant's own claim
 * configuration, mirroring gRPC.
 *
 * <p>Tests call {@code newOwnAuthorizationSearchRequest()} over REST ({@code
 * preferRestOverGrpc(true)}), which maps to {@code POST
 * /physical-tenants/<id>/v2/authentication/me/authorizations/search}: the controller resolves the
 * current principal via {@code CamundaAuthenticationProvider#getCamundaAuthentication()} (returning
 * {@code 401} when it cannot be resolved), so it actually exercises the REST bearer claim conversion
 * — unlike {@code /v2/topology}, which only validates the JWT. In-memory H2 ({@link
 * Storage#rdbmsH2}) backs the authorization store the endpoint reads; it needs no container. Tokens
 * come from the same OAuth client the gRPC counterpart uses, so they are byte-for-byte what the
 * per-tenant decoder already accepts.
 *
 * <p><b>Dependency note:</b> this test only passes once the monorepo consumes a
 * camunda-security-library version that contains the per-scope bearer-token claim-resolution fix
 * (camunda-security-library#712). Until that version bump lands it is expected to fail with the very
 * {@code 401} it guards against, which is why the PR carrying it is opened as a draft.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantRestOidcClaimMappingIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_SHARED = "realm-shared";
  private static final String CLIENT_ID_A = "client-a";
  private static final String CLIENT_SECRET_A = "secret-a";
  private static final String CLIENT_ID_B = "client-b";
  private static final String CLIENT_SECRET_B = "secret-b";
  private static final String CLIENT_ID_ROOT = "client-root";
  private static final String CLIENT_SECRET_ROOT = "secret-root";
  private static final String CLAIM_A = "client-a-id";
  private static final String CLAIM_B = "client-b-id";
  // The cluster-default claim that NO per-tenant token carries — this is what makes the pre-fix REST
  // path reject a tenant's own token with 401 (it resolves against this root claim).
  private static final String CLAIM_ROOT = "root-id";
  private static final String AUDIENCE = "zeebe";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("rest-oidc-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("rest-oidc-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("rest-oidc-tenantb"))
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
    // The cluster-default (root / default PT) resolves identity from CLAIM_ROOT, which none of the
    // per-tenant tokens carry. redirectUri must be set because Spring's OAuth2 client registration
    // requires it even in resource-server (bearer) mode where no browser redirect ever occurs.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(issuerUri);
          c.getAuthentication().getOidc().setClientId(CLIENT_ID_ROOT);
          c.getAuthentication().getOidc().setUsernameClaim(CLAIM_ROOT);
          c.getAuthentication().getOidc().setClientIdClaim(CLAIM_ROOT);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    // Both PTs share the SAME issuer; only the claim mapping differs, isolating claim mapping from
    // issuer isolation.
    configureTenantOidc(TENANT_A, issuerUri, CLAIM_A);
    configureTenantOidc(TENANT_B, issuerUri, CLAIM_B);

    BROKER.start();

    clientAOnA = oidcClient(CLIENT_ID_A, CLIENT_SECRET_A, TENANT_A, tempDir);
    clientAOnB = oidcClient(CLIENT_ID_A, CLIENT_SECRET_A, TENANT_B, tempDir);
    clientBOnB = oidcClient(CLIENT_ID_B, CLIENT_SECRET_B, TENANT_B, tempDir);
    clientBOnA = oidcClient(CLIENT_ID_B, CLIENT_SECRET_B, TENANT_A, tempDir);

    // Readiness: wait until PT-A accepts its own token over REST — i.e. the per-tenant claim
    // resolution is wired and the broker is serving. This is exactly the #64685 behaviour.
    Awaitility.await("PT-A accepts its own token over REST")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(() -> clientAOnA.newOwnAuthorizationSearchRequest().send().join());
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(clientAOnA, clientAOnB, clientBOnB, clientBOnA);
  }

  @Test
  void shouldAcceptTenantOwnTokenOverRest() {
    // The exact #64685 regression: a token issued for PT-A's own provider must be accepted on PT-A's
    // REST surface. Before the fix the REST path resolved it with the cluster-default (CLAIM_ROOT),
    // found neither claim, and rejected it with 401; gRPC accepted the same token.
    assertThatNoException()
        .as("client-a token accepted on PT-A over REST — PT-A's own claim (client-a-id) resolves")
        .isThrownBy(() -> clientAOnA.newOwnAuthorizationSearchRequest().send().join());

    assertThatNoException()
        .as("client-b token accepted on PT-B over REST — PT-B's own claim (client-b-id) resolves")
        .isThrownBy(() -> clientBOnB.newOwnAuthorizationSearchRequest().send().join());
  }

  @Test
  void shouldRejectTokenOnTenantThatDoesNotMapItsClaimOverRest() {
    // Isolation is preserved: a token is accepted only on the PT whose claim it actually carries. On
    // the other PT neither of that tenant's claims resolves, so the principal cannot be built and the
    // request is rejected with a client error. The exact code depends on where resolution runs: the
    // /me/authorizations/search controller resolves the principal lazily, so the unresolvable-claims
    // IllegalArgumentException surfaces as 400; an endpoint that resolves it inside the security
    // filter would surface 401. Either way the token is rejected — what matters for isolation.
    assertThatThrownBy(() -> clientAOnB.newOwnAuthorizationSearchRequest().send().join())
        .as("client-a token rejected on PT-B over REST — PT-B maps client-b-id, not on this token")
        .isInstanceOfSatisfying(
            ClientHttpException.class, e -> assertThat(e.code()).isIn(400, 401));

    assertThatThrownBy(() -> clientBOnA.newOwnAuthorizationSearchRequest().send().join())
        .as("client-b token rejected on PT-A over REST — PT-A maps client-a-id, not on this token")
        .isInstanceOfSatisfying(
            ClientHttpException.class, e -> assertThat(e.code()).isIn(400, 401));
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
    final var clientRoot = clientWithHardcodedClaim(CLIENT_ID_ROOT, CLIENT_SECRET_ROOT, CLAIM_ROOT);
    final var clientA = clientWithHardcodedClaim(CLIENT_ID_A, CLIENT_SECRET_A, CLAIM_A);
    final var clientB = clientWithHardcodedClaim(CLIENT_ID_B, CLIENT_SECRET_B, CLAIM_B);

    final var realmRepresentation = new RealmRepresentation();
    realmRepresentation.setRealm(REALM_SHARED);
    realmRepresentation.setEnabled(true);
    realmRepresentation.setClients(List.of(clientRoot, clientA, clientB));

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
        .preferRestOverGrpc(true)
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
