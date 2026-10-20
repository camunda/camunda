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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Logs in through the browser authorization code flow, with the real login callback, on the root
 * and on two physical tenants that share one issuer, one client and the registration id {@code
 * oidc}. Each tenant maps the username from its own claim, so the username returned by {@code
 * /v2/authentication/me} shows which tenant's settings the login used. The login callback runs on
 * the tenant of its path only if the tenant filter has stamped it by then, which unit tests that
 * bind the tenant by hand cannot show.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantOidcLoginIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM = "realm-login";
  private static final String CLIENT_ID = "camunda-login";
  private static final String CLIENT_SECRET = "login-secret";
  private static final String USERNAME = "login-user";
  private static final String PASSWORD = "login-password";
  private static final String CLAIM_ROOT = "name-root";
  private static final String CLAIM_A = "name-a";
  private static final String CLAIM_B = "name-b";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("login-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("login-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("login-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  @BeforeAll
  static void startBroker() {
    configureRealm();

    final String issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    BROKER.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri);
          oidc.setClientId(CLIENT_ID);
          oidc.setClientSecret(CLIENT_SECRET);
          oidc.setUsernameClaim(CLAIM_ROOT);
          oidc.setRedirectUri("{baseUrl}/sso-callback");
        });
    // Both tenants inherit issuer, client and secret from the root and set only their own username
    // claim, so the registration id "oidc" is shared by the root and both tenants.
    configureTenantUsernameClaim(TENANT_A, CLAIM_A);
    configureTenantUsernameClaim(TENANT_B, CLAIM_B);

    BROKER.start();

    Awaitility.await("the tenant login is served")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        newBrowser()
                            .send(
                                get("/physical-tenants/" + TENANT_A + "/oauth2/authorization/oidc"),
                                BodyHandlers.discarding())
                            .statusCode())
                    .isEqualTo(302));
  }

  @ParameterizedTest(name = "login on \"{0}\" maps the username from {1}")
  @CsvSource(
      value = {
        "'',name-root,root-name",
        "/physical-tenants/tenanta,name-a,alice-a",
        "/physical-tenants/tenantb,name-b,bob-b"
      })
  void shouldMapTheUsernameWithTheSettingsOfTheTenantTheLoginHappensOn(
      final String pathPrefix, final String claim, final String expectedUsername) throws Exception {
    // given a browser that has not logged in

    // when it completes the authorization code flow on the tenant
    final var username = loginAndReadUsername(pathPrefix);

    // then the username comes from the claim that tenant maps
    assertThat(username).as("username mapped from claim %s", claim).isEqualTo(expectedUsername);
  }

  private static String loginAndReadUsername(final String pathPrefix) throws Exception {
    final var app = newBrowser();
    final var keycloak = newBrowser();

    // the app answers the login start with a redirect to the IdP and remembers the request
    final var authorizationRedirect =
        app.send(get(pathPrefix + "/oauth2/authorization/oidc"), BodyHandlers.discarding());
    assertThat(authorizationRedirect.statusCode()).isEqualTo(302);
    final var authorizationUrl = location(authorizationRedirect.headers().map());

    // the user signs in on the IdP's login form
    final var loginPage =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(authorizationUrl)).GET(), BodyHandlers.ofString());
    final var form = Pattern.compile("<form[^>]*kc-form-login[^>]*action=\"([^\"]+)\"");
    final var matcher = form.matcher(loginPage.body());
    assertThat(matcher.find()).as("Keycloak login form in %s", loginPage.body()).isTrue();
    final var signIn =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(matcher.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=" + USERNAME + "&password=" + PASSWORD)),
            BodyHandlers.ofString());
    assertThat(signIn.statusCode())
        .as(
            "sign-in response: %s",
            signIn.body().substring(0, Math.min(300, signIn.body().length())))
        .isEqualTo(302);
    final var callbackUrl = location(signIn.headers().map());
    assertThat(URI.create(callbackUrl).getPath()).isEqualTo(pathPrefix + "/sso-callback");
    assertThat(callbackUrl).contains("code=");

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
    assertThat(me.statusCode()).as("GET me: %s", me.body()).isEqualTo(200);
    return new ObjectMapper().readTree(me.body()).get("username").asText();
  }

  private static Browser newBrowser() {
    return new Browser();
  }

  private static HttpRequest.Builder get(final String path) {
    return HttpRequest.newBuilder(BROKER.restAddress().resolve(path))
        .header("Accept", "text/html")
        .GET();
  }

  private static String location(final Map<String, List<String>> headers) {
    return headers.get("location").get(0);
  }

  private static void configureTenantUsernameClaim(final String tenantId, final String claim) {
    BROKER.withPtConfig(
        tenantId, c -> c.getSecurity().getAuthentication().getOidc().setUsernameClaim(claim));
    // selects the tenant's default slot (authentication.oidc.*) as its provider; required for every
    // non-default tenant under OIDC authentication
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static void configureRealm() {
    final var client = new ClientRepresentation();
    client.setClientId(CLIENT_ID);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(CLIENT_SECRET);
    client.setStandardFlowEnabled(true);
    client.setRedirectUris(List.of("*"));
    client.setProtocolMappers(
        List.of(
            hardcodedClaim(CLAIM_ROOT, "root-name"),
            hardcodedClaim(CLAIM_A, "alice-a"),
            hardcodedClaim(CLAIM_B, "bob-b")));

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
    realm.setRealm(REALM);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String claimName, final String claimValue) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(claimName + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", claimName,
            "claim.value", claimValue,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "true"));
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
