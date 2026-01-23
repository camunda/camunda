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
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.config.OidcAuthenticationConfigurationRepository.REGISTRATION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.authentication.config.controllers.TestApiController;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Test for OIDC authentication flow using client_secret_basic client authentication.
 *
 * <p>Tests the Spring Security OIDC authorization code flow from IdP perspective where the client
 * authenticates to the token endpoint using a client secret.
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
      "camunda.security.authentication.oidc.client-id="
          + OidcClientSecretBasicKeycloakTest.CLIENT_ID,
      "camunda.security.authentication.oidc.client-secret="
          + OidcClientSecretBasicKeycloakTest.CLIENT_SECRET,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.oidc.resource=https://api.example.com/app1/, https://api.example.com/app2/",
      "logging.level.io.camunda.authentication.config=DEBUG"
      // essential for debugging the flow
      //      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
class OidcClientSecretBasicKeycloakTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .failOnUnmatchedRequests(true)
          .build();

  static final String CLIENT_ID = "camunda-client";
  static final String CLIENT_SECRET = "camunda-client-secret";
  static final String REALM = "camunda-test";
  static final String ENDPOINT_WELL_KNOWN_JWKS = "/realms/" + REALM + "/.well-known/jwks.json";
  static final String ENDPOINT_WELL_KNOWN_OIDC =
      "/realms/" + REALM + "/.well-known/openid-configuration";
  static final String ENDPOINT_TOKEN = "/realms/" + REALM + "/oauth/token";

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
        .hasHeader("Location", "http://localhost/oauth2/authorization/oidc");
  }

  @Test
  public void shouldSendClientAssertionToTokenEndpointDuringAuthCodeExchange() {
    stubIdpEndpoints();

    // with an established session, we notify Spring Security we want to authenticate
    // which builds and saves an authorizationRequest
    // and we get a state reference that can be matched upon the redirect from the IdP
    final MockHttpSession session = new MockHttpSession();
    final var state = beginAuthenticationFlow(session);

    // when inducing an auth code for token exchange
    mockAuthenticatedRedirectFromIdp(session, state);

    // then the IdP receives the authorization code
    // with the Spring Security client authenticating using client_secret_basic
    verifyRequestStructure();
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
            .withBasicAuth(new BasicCredentials(CLIENT_ID, CLIENT_SECRET))
            .withRequestBody(containing("grant_type=authorization_code"))
            .withRequestBody(containing("code=test_authorization_code"))
            .withRequestBody(
                containing(
                    "resource=https%3A%2F%2Fapi.example.com%2Fapp1%2F&resource=https%3A%2F%2Fapi.example.com%2Fapp2%2F"))
            .withRequestBody(notContaining("client_assertion_type"))
            .withRequestBody(notContaining("client_assertion")));
  }

  @Test
  public void shouldSendBasicBearerTokenToTokenEndpointDuringRefreshToken() {
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

    mockMvcTester
        .get()
        .uri(TestApiController.DUMMY_WEBAPP_ENDPOINT)
        .accept(MediaType.TEXT_HTML)
        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticationToken))
        .session(session)
        .exchange();

    verify(
        1,
        postRequestedFor(urlEqualTo(ENDPOINT_TOKEN))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withBasicAuth(new BasicCredentials(CLIENT_ID, CLIENT_SECRET))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(notContaining("client_assertion"))
            .withRequestBody(containing("refresh_token=refresh_token_value")));
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
