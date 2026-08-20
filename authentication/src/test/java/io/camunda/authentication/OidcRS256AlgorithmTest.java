/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.OidcRS256AlgorithmTest.CLIENT_ID;
import static io.camunda.authentication.OidcRS256AlgorithmTest.CLIENT_SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

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
      "camunda.security.authentication.oidc.client-id=" + CLIENT_ID,
      "camunda.security.authentication.oidc.client-secret=" + CLIENT_SECRET,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.oidc.resource=https://api.example.com/app1/, https://api.example.com/app2/",
      "logging.level.io.camunda.authentication.config=DEBUG"
      // essential for debugging the flow
      //      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
public class OidcRS256AlgorithmTest {

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
  static final String ENDPOINT_USERINFO = "/realms/" + REALM + "/userinfo";
  static final String ENDPOINT_TOKEN = "/realms/" + REALM + "/oauth/token";

  // Test RSA JWK used to sign ID tokens and exposed via JWKS
  private static RSAKey rsaJwk;
  // The nonce query parameter from the authorization redirect is already the SHA-256 hash Spring
  // Security computes from the raw nonce it stores server-side; the ID token's nonce claim must
  // echo this same hashed value verbatim for OidcAuthorizationCodeAuthenticationProvider's
  // validation to succeed.
  private static String expectedNonceHash;

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/realms/" + REALM);
  }

  @BeforeAll
  static void stubWellKnownForStartup() throws JOSEException {
    rsaJwk =
        new RSAKeyGenerator(2048)
            .keyID("test-kid")
            .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_OIDC))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(wellKnownResponse())));
  }

  /**
   * Test that the OIDC authentication flow works end-to-end when the IdP uses default RS256-signed
   * ID token
   */
  @Test
  public void shouldUseCorrectAlgorithmToVerifyIdToken() {
    // begin the authorization redirect, capturing its state/nonce and the resulting session cookie
    final var authorizationRedirect = beginAuthenticationFlow();
    expectedNonceHash = authorizationRedirect.nonceHash();

    stubIdpEndpoints();
    mockAuthenticatedRedirectFromIdp(authorizationRedirect);
  }

  private static void stubIdpEndpoints() {
    stubFor(
        post(urlEqualTo(ENDPOINT_TOKEN))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(tokenResponse())));

    final String jwksBody =
        JSONObjectUtils.toJSONString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_JWKS))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(jwksBody)));

    stubFor(
        get(urlEqualTo(ENDPOINT_USERINFO))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(userInfoResponse())));
  }

  private AuthorizationRedirect beginAuthenticationFlow() {
    final var redirectResult =
        mockMvcTester
            .get()
            .uri("/oauth2/authorization/oidc")
            .accept(MediaType.TEXT_HTML)
            .exchange();

    assertThat(redirectResult).hasStatus3xxRedirection();
    final var redirectUrl = redirectResult.getResponse().getHeader("Location");
    final var queryParams =
        UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams();
    final var sessionCookie =
        redirectResult.getResponse().getCookie(CamundaSecurityFilterChainConstants.SESSION_COOKIE);
    assertThat(sessionCookie).isNotNull();

    return new AuthorizationRedirect(
        URLDecoder.decode(
            Objects.requireNonNull(queryParams.getFirst("state")), StandardCharsets.UTF_8),
        URLDecoder.decode(
            Objects.requireNonNull(queryParams.getFirst("nonce")), StandardCharsets.UTF_8),
        sessionCookie);
  }

  private void mockAuthenticatedRedirectFromIdp(final AuthorizationRedirect authorizationRedirect) {
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("/sso-callback")
            .accept(MediaType.TEXT_HTML)
            .cookie(authorizationRedirect.sessionCookie())
            .queryParam("code", "test_authorization_code")
            .queryParam("state", authorizationRedirect.state())
            .queryParam("session_state", "test_session_state")
            .queryParam("iss", "http://localhost:" + wireMock.getPort() + "/realms/" + REALM)
            .exchange();

    assertThat(result.getUnresolvedException()).isNull();
    assertThat(result).hasStatus3xxRedirection();
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

  private static String userInfoResponse() {
    return """
      {
        "sub": "1234567890",
        "name": "Camundo Camundovski",
        "preferred_username": "camundo",
        "email": "camundo@example.com"
      }
      """;
  }

  private static String tokenResponse() {
    // Build a minimal RS256-signed ID token with kid matching the JWKS and the expected nonce hash
    final String issuer = "http://localhost:" + wireMock.getPort() + "/realms/" + REALM;
    final var now = new java.util.Date();
    final var exp = new java.util.Date(now.getTime() + 3600_000L);
    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(CLIENT_ID)
            .subject("1234567890")
            .claim("name", "Camundo Camundovski")
            .claim("admin", true)
            .issueTime(now)
            .expirationTime(exp)
            .claim("nonce", expectedNonceHash)
            .build();

    final SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(), claims);
    try {
      signedJWT.sign(new RSASSASigner(rsaJwk.toPrivateKey()));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final String idToken = signedJWT.serialize();

    return """
            {
                "access_token": "dummy-access-token",
                "expires_in": 3600,
                "token_type": "Bearer",
                "id_token": "%s"
            }
        """
        .formatted(idToken);
  }

  /**
   * Holds what the test needs from the initial authorization redirect: {@code state} and {@code
   * nonceHash} are plain query parameters on the redirect to the IdP (standard OIDC behaviour, not
   * something read back out of the session — {@code nonceHash} is already the SHA-256 hash Spring
   * Security computed from the raw nonce it stores server-side), and {@code sessionCookie} is the
   * real Spring-Session-backed cookie the server issued — it must be replayed on the follow-up
   * request so the server resolves the same session (and the {@code OAuth2AuthorizationRequest}
   * stored in it) rather than starting a new one.
   */
  private record AuthorizationRedirect(String state, String nonceHash, Cookie sessionCookie) {}
}
