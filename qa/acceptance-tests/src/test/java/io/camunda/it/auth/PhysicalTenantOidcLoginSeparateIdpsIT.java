/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Logs in through the browser authorization code flow on two physical tenants that share the
 * registration id {@code oidc} but use separate identity providers: {@code tenanta} inherits the
 * root's realm, and {@code tenantb} overrides the issuer, client and secret with another realm. So
 * the two have a different issuer and different signing keys.
 *
 * <p>The username comes from a claim that only the access token carries. If a login decoded its
 * access token with the settings of another tenant, the decode fails and the login falls back to
 * the ID token, which does not have the claim. The decoders are kept until the application stops,
 * so the first login decides for the later ones, as it did before camunda-security-library#720.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantOidcLoginSeparateIdpsIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_A = "realm-sep-a";
  private static final String REALM_B = "realm-sep-b";
  private static final String CLIENT_A = "camunda-sep-a";
  private static final String CLIENT_B = "camunda-sep-b";
  private static final String SECRET_A = "secret-a";
  private static final String SECRET_B = "secret-b";
  private static final String USERNAME = "login-user";
  private static final String PASSWORD = "login-password";
  private static final String ACCESS_TOKEN_CLAIM = "access_token_user";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("sep-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("sep-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("sep-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  @BeforeAll
  static void startBroker() {
    createRealm(REALM_A, CLIENT_A, SECRET_A, "alice-a");
    createRealm(REALM_B, CLIENT_B, SECRET_B, "bob-b");

    BROKER.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_A));
          oidc.setClientId(CLIENT_A);
          oidc.setClientSecret(SECRET_A);
          oidc.setUsernameClaim(ACCESS_TOKEN_CLAIM);
          oidc.setRedirectUri("{baseUrl}/sso-callback");
        });
    assignDefaultSlot(TENANT_A);
    assignDefaultSlot(TENANT_B);
    BROKER.withPtConfig(
        TENANT_B,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_B));
          oidc.setClientId(CLIENT_B);
          oidc.setClientSecret(SECRET_B);
        });

    BROKER.start();

    Awaitility.await("the tenant login is served")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        new Browser()
                            .send(
                                get("/physical-tenants/" + TENANT_B + "/oauth2/authorization/oidc"),
                                BodyHandlers.discarding())
                            .statusCode())
                    .isEqualTo(302));
  }

  @Test
  void shouldDecodeTheAccessTokenOfEachTenantWithItsOwnIdentityProvider() throws Exception {
    // given the first login creates the access-token decoder for the registration id "oidc"
    final var usernameOnTenantA = loginAndReadUsername("/physical-tenants/" + TENANT_A);

    // when a tenant on another identity provider logs in with the same registration id
    final var usernameOnTenantB = loginAndReadUsername("/physical-tenants/" + TENANT_B);
    final var usernameOnTenantAAgain = loginAndReadUsername("/physical-tenants/" + TENANT_A);

    // then each tenant reads the claim from its own access token
    assertThat(usernameOnTenantA).isEqualTo("alice-a");
    assertThat(usernameOnTenantB).isEqualTo("bob-b");
    assertThat(usernameOnTenantAAgain).isEqualTo("alice-a");
  }

  private static String loginAndReadUsername(final String pathPrefix) throws Exception {
    final var app = new Browser();
    final var keycloak = new Browser();

    // the app answers the login start with a redirect to the IdP and remembers the request
    final var authorizationRedirect =
        app.send(get(pathPrefix + "/oauth2/authorization/oidc"), BodyHandlers.discarding());
    assertThat(authorizationRedirect.statusCode()).isEqualTo(302);
    final var authorizationUrl = location(authorizationRedirect.headers().map());

    // the user signs in on the IdP's login form
    final var loginPage =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(authorizationUrl)).GET(), BodyHandlers.ofString());
    final var matcher =
        Pattern.compile("<form[^>]*kc-form-login[^>]*action=\"([^\"]+)\"")
            .matcher(loginPage.body());
    assertThat(matcher.find()).as("Keycloak login form in %s", loginPage.body()).isTrue();
    final var signIn =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(matcher.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=" + USERNAME + "&password=" + PASSWORD)),
            BodyHandlers.ofString());
    assertThat(signIn.statusCode()).isEqualTo(302);
    final var callbackUrl = location(signIn.headers().map());
    assertThat(URI.create(callbackUrl).getPath()).isEqualTo(pathPrefix + "/sso-callback");

    // the browser returns to the app's callback with the session of the login start
    final var callback =
        app.send(HttpRequest.newBuilder(URI.create(callbackUrl)).GET(), BodyHandlers.discarding());
    assertThat(callback.statusCode()).isEqualTo(302);

    final var me =
        app.send(
            HttpRequest.newBuilder(
                    BROKER.restAddress().resolve(pathPrefix + "/v2/authentication/me"))
                .header("Accept", "application/json")
                .GET(),
            BodyHandlers.ofString());
    assertThat(me.statusCode()).as("GET me on %s: %s", pathPrefix, me.body()).isEqualTo(200);
    return new ObjectMapper().readTree(me.body()).get("username").asText();
  }

  private static HttpRequest.Builder get(final String path) {
    return HttpRequest.newBuilder(BROKER.restAddress().resolve(path))
        .header("Accept", "text/html")
        .GET();
  }

  private static String location(final Map<String, List<String>> headers) {
    return headers.get("location").get(0);
  }

  private static String issuerUri(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static void assignDefaultSlot(final String tenantId) {
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static void createRealm(
      final String realmName,
      final String clientId,
      final String secret,
      final String accessTokenUser) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(secret);
    client.setStandardFlowEnabled(true);
    client.setRedirectUris(List.of("*"));
    client.setProtocolMappers(List.of(accessTokenOnlyClaim(accessTokenUser)));

    final var password = new CredentialRepresentation();
    password.setType(CredentialRepresentation.PASSWORD);
    password.setValue(PASSWORD);
    password.setTemporary(false);
    final var user = new UserRepresentation();
    user.setUsername(USERNAME);
    user.setEnabled(true);
    user.setEmail("login-user@example.com");
    user.setEmailVerified(true);
    user.setFirstName("Login");
    user.setLastName("User");
    user.setCredentials(List.of(password));

    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  /**
   * A claim only in the access token, not in the ID token or the UserInfo response, so that the
   * fallback to the ID token cannot provide it.
   */
  private static ProtocolMapperRepresentation accessTokenOnlyClaim(final String value) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(ACCESS_TOKEN_CLAIM + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", ACCESS_TOKEN_CLAIM,
            "claim.value", value,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "false",
            "userinfo.token.claim", "false"));
    return mapper;
  }

  /**
   * A browser stand-in that replays the cookies a server set, by name only. The JDK cookie manager
   * does not send Keycloak's login cookies back over plain HTTP.
   */
  private static final class Browser {
    private final HttpClient client =
        HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    <T> HttpResponse<T> send(final HttpRequest.Builder request, final BodyHandler<T> handler)
        throws Exception {
      if (!cookies.isEmpty()) {
        request.header(
            "Cookie",
            cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; ")));
      }
      final var response = client.send(request.build(), handler);
      response.headers().allValues("set-cookie").forEach(this::store);
      return response;
    }

    private void store(final String setCookie) {
      final var attributes = setCookie.split(";");
      final var nameAndValue = attributes[0].split("=", 2);
      final var name = nameAndValue[0].trim();
      final var expired =
          nameAndValue.length < 2
              || nameAndValue[1].isBlank()
              || Arrays.stream(attributes)
                  .skip(1)
                  .anyMatch(attribute -> attribute.trim().equalsIgnoreCase("Max-Age=0"));
      if (expired) {
        cookies.remove(name);
      } else {
        cookies.put(name, nameAndValue[1].trim());
      }
    }
  }
}
