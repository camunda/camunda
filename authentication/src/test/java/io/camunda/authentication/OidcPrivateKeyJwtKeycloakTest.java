/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.config.OidcAuthenticationConfigurationRepository.REGISTRATION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Test for OIDC authentication flow using private_key_jwt client authentication.
 *
 * <p>Tests the Spring Security OIDC authorization code flow from IdP perspective where the client
 * authenticates to the token endpoint using a JWT signed with a private key.
 *
 * <p>The test verifies the correct JWT format and claims in client assertion adhering to the <a
 * href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OIDC
 * specification</a>,<a
 * href="https://www.keycloak.org/securing-apps/authz-client#_client_authentication_with_signed_jwt">
 * Keycloak documentation</a>, and <a
 * href="https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials">MS Entra
 * documentation</a> <hr>
 *
 * <p>Header expectations by IdP set as default:
 *
 * <ul>
 *   <li>Keycloak: either NO <code>kid</code> or public key SPKI SHA-256 as <code>kid</code>. We
 *       default to setting a <code>kid</code> for unambiguous key selection and easier
 *       auditing/logging.
 *   <li>MS Entra: expect one of: <code>x5t</code> certificate SHA-1 thumbprint, <code>x5t#S256
 *       </code> certificate SHA-256 thumbprint, or <code>kid</code> that is the certificate SHA-1
 *       thumbprint. As the <code>x5t#S256</code> is explicitly defined in <a
 *       href="https://datatracker.ietf.org/doc/html/rfc7517#section-4.9">RFC7517</a>, we use it as
 *       the default for broad interoperability. With this header parameter set, Entra will ignore
 *       the <code>kid</code>.
 *   <li>Other: some providers have different requirements for the <code>kid</code>, which is then
 *       customizable using configuration properties. Outside of scope for this test.
 * </ul>
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(
    classes = {
      OidcFlowTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=" + OidcPrivateKeyJwtKeycloakTest.CLIENT_ID,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.oidc.clientAuthenticationMethod="
          + OidcAuthenticationConfiguration.CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT,
      "camunda.security.authentication.oidc.assertion.keystore.path= ${user.dir}/src/test/resources/keystore.p12",
      "camunda.security.authentication.oidc.assertion.keystore.password=password",
      "camunda.security.authentication.oidc.assertion.keystore.keyAlias=camunda-standalone",
      "camunda.security.authentication.oidc.assertion.keystore.keyPassword=password",
      "camunda.security.authentication.oidc.resource=https://api.example.com/app1/, https://api.example.com/app2/",
      "logging.level.io.camunda.authentication.config=DEBUG",
      // essential for debugging the flow
      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
class OidcPrivateKeyJwtKeycloakTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .failOnUnmatchedRequests(true)
          .build();

  static final String CLIENT_ID = "camunda-client";
  static final String REALM = "camunda-test";
  static final String ENDPOINT_WELL_KNOWN_JWKS = "/realms/" + REALM + "/.well-known/jwks.json";
  static final String ENDPOINT_WELL_KNOWN_OIDC =
      "/realms/" + REALM + "/.well-known/openid-configuration";
  static final String ENDPOINT_TOKEN = "/realms/" + REALM + "/oauth/token";
  static final String EXPECTED_KEY_ID = "opaYc1PqzH6XYGbL3KF4BK1rkNRS4IuMAfh3qPZILHo";
  static final Base64URL EXPECTED_X5T_S256 =
      Base64URL.from("gCC_MwKDLUCxMYUlm95bDX8ol6nNHhCohhudSkJAJhQ");
  static final String REGEX_JWT = "[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*";
  final String expectedTokenEndpointUrl = "http://localhost:" + wireMock.getPort() + ENDPOINT_TOKEN;

  @Autowired MockMvcTester mockMvcTester;
  @Autowired OAuth2AuthorizedClientService authorizedClientService;
  @Autowired ClientRegistrationRepository clientRegistrationRepository;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/realms/" + REALM);
  }

  @BeforeAll
  static void stubWellKnownForStartup() {
    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_OIDC))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(wellKnownResponse())));
  }

  @Test
  public void shouldRedirectToOidcAuthorizationEndpointWhenAccessingRoot() {
    final MvcTestResult result =
        mockMvcTester.get().uri("/").accept(MediaType.TEXT_HTML).exchange();
    assertThat(result)
        .hasStatus(HttpStatus.FOUND)
        .hasHeader("Location", "/oauth2/authorization/oidc");
  }

  @Test
  public void shouldSendClientAssertionToTokenEndpointDuringAuthCodeExchange()
      throws ParseException {
    stubIdpEndpoints();

    // with an established session, we notify Spring Security we want to authenticate
    // which builds and saves an authorizationRequest
    // and we get a state reference that can be matched upon the redirect from the IdP
    final MockHttpSession session = new MockHttpSession();
    final var state = beginAuthenticationFlow(session);

    // when inducing an auth code for token exchange
    mockAuthenticatedRedirectFromIdp(session, state);

    // then the IdP receives the authorization code
    // with the Spring Security client authenticating using private_key_jwt
    // in the form of a client id and client assertion
    verifyRequestStructure();
    verifyClientAssertion();
  }

  @Test
  public void shouldSendClientAssertionToTokenEndpointDuringRefreshToken() {
    stubIdpEndpoints();

    final var principalName = "a_user";
    final var client = createExpiredAuthorizedClientWithRefreshToken(principalName);

    final var oauth2User =
        new DefaultOAuth2User(
            Set.of(() -> "USER"),
            java.util.Map.of("sub", principalName, "name", "Test User"),
            "sub");

    final var authenticationToken =
        new OAuth2AuthenticationToken(oauth2User, Set.of(() -> "USER"), REGISTRATION_ID);
    authenticationToken.setAuthenticated(true);
    authorizedClientService.saveAuthorizedClient(client, authenticationToken);

    final var session = new MockHttpSession();
    final SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authenticationToken);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    session.setAttribute(
        "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS",
        Map.of(REGISTRATION_ID, client));

    final var result =
        mockMvcTester
            .get()
            .uri(TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .accept(MediaType.TEXT_HTML)
            .with(SecurityMockMvcRequestPostProcessors.authentication(authenticationToken))
            .session(session)
            .exchange();

    // If not implemented, the exception will be
    // java.lang.IllegalArgumentException: This class supports `client_secret_basic`,
    // `client_secret_post`, and `none` by default. Client [oidc] is using [private_key_jwt]
    // instead. Please use a supported client authentication method, or use
    // `setRequestEntityConverter` to supply an instance that supports [private_key_jwt].
    assertThat(result.getUnresolvedException()).isNotInstanceOf(IllegalArgumentException.class);

    verify(
        1,
        postRequestedFor(urlEqualTo(ENDPOINT_TOKEN))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("client_id=" + CLIENT_ID))
            .withRequestBody(containing("refresh_token=refresh_token_value"))
            .withRequestBody(
                containing(
                    "client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"))
            .withRequestBody(matching(".*client_assertion=" + REGEX_JWT + ".*"))
            .withRequestBody(notContaining("client_secret")));
  }

  private static void stubIdpEndpoints() {
    // having an IdP token endpoint
    stubFor(
        post(urlEqualTo(ENDPOINT_TOKEN))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(tokenResponse())));

    // and a dummy jwks well-known endpoint to prevent Spring from breaking
    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_JWKS))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));
  }

  private String beginAuthenticationFlow(final MockHttpSession session) {
    final var redirectResult =
        mockMvcTester
            .get()
            .uri("/oauth2/authorization/oidc")
            .accept(MediaType.TEXT_HTML)
            .session(session)
            .exchange();
    assertThat(redirectResult).hasStatus3xxRedirection();
    final String redirectUrl = redirectResult.getResponse().getHeader("Location");
    assertThat(redirectUrl).isNotNull();
    final var queryParams =
        UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams();
    assertThat(queryParams).containsKey("state");
    return URLDecoder.decode(
        Objects.requireNonNull(queryParams.getFirst("state")), StandardCharsets.UTF_8);
  }

  private void mockAuthenticatedRedirectFromIdp(final MockHttpSession session, final String state) {
    mockMvcTester
        .get()
        .uri("/sso-callback")
        .accept(MediaType.TEXT_HTML)
        .session(session)
        .queryParam("code", "test_authorization_code")
        .queryParam("state", state)
        .queryParam("session_state", "test_session_state")
        .queryParam("iss", "http://localhost:" + wireMock.getPort() + "/realms/" + REALM)
        .exchange();
  }

  private void verifyRequestStructure() {
    verify(
        1,
        postRequestedFor(urlEqualTo(ENDPOINT_TOKEN))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(containing("grant_type=authorization_code"))
            .withRequestBody(containing("code=test_authorization_code"))
            .withRequestBody(containing("client_id=" + CLIENT_ID))
            .withRequestBody(
                containing(
                    "resource=https%3A%2F%2Fapi.example.com%2Fapp1%2F&resource=https%3A%2F%2Fapi.example.com%2Fapp2%2F"))
            .withRequestBody(
                containing(
                    "client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"))
            // client_assertion=<A JWT>
            .withRequestBody(matching(".*client_assertion=" + REGEX_JWT + ".*"))
            .withRequestBody(notContaining("client_secret")));
  }

  private void verifyClientAssertion() throws ParseException {
    final var clientAssertion = getClientAssertionFromLastRequest();
    final var jwt = SignedJWT.parse(clientAssertion);

    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(EXPECTED_KEY_ID); // public key SHA-256
    assertThat(jwt.getHeader().getX509CertSHA256Thumbprint()).isEqualTo(EXPECTED_X5T_S256);

    // Keycloak requirements and OIDC specification match
    // https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication
    final var payload = jwt.getPayload().toJSONObject();
    final var now = Instant.now().getEpochSecond();
    assertThat(payload)
        .containsEntry("iss", CLIENT_ID)
        .containsEntry("sub", CLIENT_ID)
        .containsEntry("aud", expectedTokenEndpointUrl)
        .containsKey("jti")
        .hasEntrySatisfying(
            "iat",
            iat -> assertThat(now).as("issued in the past").isGreaterThanOrEqualTo((Long) iat))
        .hasEntrySatisfying(
            "exp", exp -> assertThat(now).as("expires in the future").isLessThan((Long) exp));

    assertThat(jwt.getSignature()).isNotNull(); // null would mean no signature exists
  }

  private String getClientAssertionFromLastRequest() {
    final var tokenEvents = wireMock.findAll(postRequestedFor(urlEqualTo(ENDPOINT_TOKEN)));
    assertThat(tokenEvents).hasSize(1);

    final var exchangeEvent = tokenEvents.getFirst();
    return Arrays.stream(exchangeEvent.getBodyAsString().split("&"))
        .filter(field -> field.startsWith("client_assertion="))
        .map(field -> field.split("=")[1])
        .findFirst()
        .orElseThrow();
  }

  private OAuth2AuthorizedClient createExpiredAuthorizedClientWithRefreshToken(
      final String principalName) {
    final var now = Instant.now();
    final var expiredAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access_token",
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            Set.of("read", "write"));

    final var refreshToken =
        new OAuth2RefreshToken(
            "refresh_token_value", now.minus(2, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

    final var clientRegistration =
        clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);

    return new OAuth2AuthorizedClient(
        clientRegistration, principalName, expiredAccessToken, refreshToken);
  }

  private static String wellKnownResponse() {
    return """
            {
                "issuer": "http://localhost:000000/realms/KEYCLOAKREALM",
                "authorization_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/oauth/authorize",
                "token_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/oauth/token",
                "userinfo_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/userinfo",
                "jwks_uri": "http://localhost:000000/realms/KEYCLOAKREALM/.well-known/jwks.json",
                "response_types_supported": [
                    "code",
                    "token",
                    "id_token",
                    "code token",
                    "code id_token",
                    "token id_token",
                    "code token id_token",
                    "none"
                ],
                "subject_types_supported": [
                    "public"
                ],
                "id_token_signing_alg_values_supported": [
                    "RS256"
                ],
                "scopes_supported": [
                    "openid",
                    "email",
                    "profile"
                ]
            }
        """
        .replaceAll("000000", String.valueOf(wireMock.getPort()))
        .replaceAll("KEYCLOAKREALM", REALM);
  }

  private static String tokenResponse() {
    return """
            {
                "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkNhbXVuZG8gQ2FtdW5kb3Zza2kiLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNzU2OTQwMjk5fQ.Tih2EbvzaCkREp4yNaP7Y_opD1YyO3bE95te66bVpTXtOOl9jL8Ovv2nCNm44HMpQPZVyVlR3bDd3tcWf6mRolkeFMrhe2mkz0xr_-WptpgkigrIKVhWZzjz6YEawJpYFNH6pwn74WVPlnruyzeqnItdcbM-0dQ9gsIOPPgubajKnTs1qA6NwkOWU9AI6Y5aQcflRpPewkPMHKL-KIe0lIGfLUaQhUZEAbQC7u6Pujx8l2cuom-xpWAeYHCoNfKbsssUWHY3DZ4yZGk6vQe_dLt5iYxlVcp-SSOnOKNd6EU_Rf4cD0zNWTX44YfwYM5ZYBBJd9QiwSj_xmzLLbBFmA",
                "expires_in": 3600,
                "token_type": "Bearer",
                "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkNhbXVuZG8gQ2FtdW5kb3Zza2kiLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNzU2OTQwMjk5fQ.Tih2EbvzaCkREp4yNaP7Y_opD1YyO3bE95te66bVpTXtOOl9jL8Ovv2nCNm44HMpQPZVyVlR3bDd3tcWf6mRolkeFMrhe2mkz0xr_-WptpgkigrIKVhWZzjz6YEawJpYFNH6pwn74WVPlnruyzeqnItdcbM-0dQ9gsIOPPgubajKnTs1qA6NwkOWU9AI6Y5aQcflRpPewkPMHKL-KIe0lIGfLUaQhUZEAbQC7u6Pujx8l2cuom-xpWAeYHCoNfKbsssUWHY3DZ4yZGk6vQe_dLt5iYxlVcp-SSOnOKNd6EU_Rf4cD0zNWTX44YfwYM5ZYBBJd9QiwSj_xmzLLbBFmA"
            }
        """;
  }
}
