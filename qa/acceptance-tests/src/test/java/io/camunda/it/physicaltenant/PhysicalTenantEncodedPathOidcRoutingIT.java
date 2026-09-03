/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The OIDC counterpart of {@link PhysicalTenantEncodedPathRoutingIT}, which pins the same property
 * under basic authentication.
 *
 * <p>The two authentication methods validate credentials against different things, so the
 * basic-auth result does not carry over. Basic auth resolves the user from the physical tenant the
 * request was stamped with, which is exactly the mismatched id, so it fails there. An OIDC bearer
 * token is validated by the decoder bound to the <em>filter chain</em> the request matched, and
 * that chain was selected from the decoded path — so nothing in the validation step consults the
 * stamped id, and a token valid for the configured tenant is a valid token here.
 *
 * <p>Whether the request is then refused therefore rests on a different step than it does for basic
 * auth, which is why it needs its own test rather than an argument by analogy. It is refused, and
 * the different status — 400 here against 401 there — is the visible sign that a different step
 * does it.
 *
 * <p>Secondary storage is provisioned per tenant because membership resolution — the first thing
 * downstream to act on the stamped id — is only active when it is.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantEncodedPathOidcRoutingIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String REALM = "realm-a";
  private static final String CLIENT_ID = "client-a";
  private static final String CLIENT_SECRET = "secret-a";
  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant(EncodedTenantPaths.TENANT_ID, Storage.rdbmsH2(EncodedTenantPaths.TENANT_ID))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  private static String bearerToken;

  @BeforeAll
  static void start() throws Exception {
    configureRealm();
    final var issuer = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    // redirectUri is mandatory for Spring's client registration even in resource-server mode.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(issuer);
          c.getAuthentication().getOidc().setClientId(CLIENT_ID);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    BROKER.withPtConfig(
        EncodedTenantPaths.TENANT_ID,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuer);
          oidc.setClientId(CLIENT_ID);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    BROKER.withProperty(
        "camunda.physical-tenants."
            + EncodedTenantPaths.TENANT_ID
            + ".security.authentication.providers.assigned[0]",
        "oidc");

    BROKER.start();
    bearerToken = fetchToken();

    Awaitility.await("the configured tenant's own path is served")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        get(EncodedTenantPaths.meEndpointFor(EncodedTenantPaths.TENANT_ID))
                            .statusCode())
                    .isEqualTo(200));
  }

  @Test
  void shouldNotServeAPercentEncodedTenantPath() throws Exception {
    // given — a token the configured tenant's chain accepts, proven by the baseline above

    // when — the same endpoint and the same token, with the tenant spelled two ways
    final var plain = get(EncodedTenantPaths.meEndpointFor(EncodedTenantPaths.TENANT_ID));
    final var encoded = get(EncodedTenantPaths.meEndpointFor(EncodedTenantPaths.ENCODED_TENANT_ID));

    // then
    assertThat(plain.statusCode())
        .as("a configured tenant's own path must authenticate and serve")
        .isEqualTo(200);

    // and — the encoded spelling reached the same chain, since the catch-all would have answered
    // 404, and the token validated there. It must still not be served: the id handed downstream
    // matches no configured tenant. A 200 would mean it was served as some tenant; a 5xx would mean
    // the mismatched id travelled past authentication and failed on a store it cannot resolve.
    // The status differs from the basic-auth case, which answers 401 when it cannot resolve the
    // user under the mismatched id. What matters is shared: not 200, so it was not served, and not
    // 5xx, which is what acting on an id that resolves to no store would produce.
    assertThat(encoded.statusCode())
        .as("an encoded spelling of a configured tenant must not be served")
        .isEqualTo(400);
  }

  private static HttpResponse<String> get(final String rawPath) throws Exception {
    return EncodedTenantPaths.get(
        BROKER.restAddress().toString(), rawPath, "Bearer " + bearerToken);
  }

  private static String fetchToken() throws Exception {
    final var form =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8);
    try (final var client = HttpClient.newHttpClient()) {
      final var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create(
                          KEYCLOAK.getAuthServerUrl()
                              + "/realms/"
                              + REALM
                              + "/protocol/openid-connect/token"))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(BodyPublishers.ofString(form))
                  .build(),
              BodyHandlers.ofString());
      final var matcher = ACCESS_TOKEN.matcher(response.body());
      assertThat(matcher.find()).as("Keycloak returned a token: %s", response.body()).isTrue();
      return matcher.group(1);
    }
  }

  private static void configureRealm() {
    final var client = new ClientRepresentation();
    client.setClientId(CLIENT_ID);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(CLIENT_SECRET);
    client.setServiceAccountsEnabled(true);

    final var realm = new RealmRepresentation();
    realm.setRealm(REALM);
    realm.setEnabled(true);
    realm.setClients(List.of(client));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }
}
