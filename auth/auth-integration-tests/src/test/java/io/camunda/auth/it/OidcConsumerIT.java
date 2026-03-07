/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full Spring Boot application context integration test simulating an OIDC consumer using the auth
 * library. Uses WireMock to serve OIDC discovery and JWK set endpoints, and Nimbus JOSE to generate
 * test JWTs signed with a test RSA key.
 *
 * <p>Tests the JWT resource server path (not the full OAuth2 login redirect flow, which requires a
 * real IdP).
 */
@SpringBootTest(
    classes = OidcConsumerIT.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "camunda.auth.method=oidc",
      "camunda.auth.unprotected-api=false",
      "camunda.auth.security.webapp-enabled=false",
      "spring.application.name=oidc-consumer-it"
    })
@AutoConfigureMockMvc
class OidcConsumerIT {

  private static RSAKey rsaKey;
  private static String jwkSetJson;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @DynamicPropertySource
  static void configureOidc(final DynamicPropertyRegistry registry) {
    final String baseUrl = wireMock.baseUrl();
    final String issuer = baseUrl + "/realms/test";

    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuer);
    registry.add("camunda.security.authentication.oidc.client-id", () -> "test-client");
    registry.add("camunda.security.authentication.oidc.client-secret", () -> "test-secret");
    registry.add(
        "camunda.security.authentication.oidc.token-uri",
        () -> baseUrl + "/realms/test/protocol/openid-connect/token");
    registry.add(
        "camunda.security.authentication.oidc.authorization-uri",
        () -> baseUrl + "/realms/test/protocol/openid-connect/auth");
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri",
        () -> baseUrl + "/realms/test/protocol/openid-connect/certs");
    registry.add(
        "camunda.security.authentication.oidc.redirect-uri",
        () -> "{baseUrl}/login/oauth2/code/{registrationId}");
  }

  @BeforeEach
  void setupWireMock() throws Exception {
    // Generate RSA key pair for signing JWTs (only on first call)
    if (rsaKey == null) {
      rsaKey =
          new RSAKeyGenerator(2048)
              .keyID(UUID.randomUUID().toString())
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      jwkSetJson = new JWKSet(rsaKey.toPublicJWK()).toString();
    }

    final String issuer = wireMock.baseUrl() + "/realms/test";

    // OpenID Connect discovery document
    final String discoveryJson =
        """
        {
          "issuer": "%s",
          "authorization_endpoint": "%s/protocol/openid-connect/auth",
          "token_endpoint": "%s/protocol/openid-connect/token",
          "userinfo_endpoint": "%s/protocol/openid-connect/userinfo",
          "end_session_endpoint": "%s/protocol/openid-connect/logout",
          "jwks_uri": "%s/protocol/openid-connect/certs",
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": ["RS256"],
          "response_types_supported": ["code"]
        }
        """
            .formatted(issuer, issuer, issuer, issuer, issuer, issuer);

    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/realms/test/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(discoveryJson)));

    // JWK Set endpoint
    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/realms/test/protocol/openid-connect/certs"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwkSetJson)));
  }

  @Autowired private ApplicationContext applicationContext;
  @Autowired private MockMvc mockMvc;

  // -- Auto-configuration wiring tests --

  @Test
  void shouldWireJwtDecoder() {
    assertThat(applicationContext.getBean(JwtDecoder.class)).isNotNull();
  }

  @Test
  void shouldWireOidcTokenAuthenticationConverter() {
    assertThat(applicationContext.getBean(OidcTokenAuthenticationConverter.class)).isNotNull();
  }

  @Test
  void shouldWireCamundaAuthenticationProvider() {
    assertThat(applicationContext.getBean(CamundaAuthenticationProvider.class)).isNotNull();
  }

  @Test
  void shouldWireAuthFailureHandler() {
    assertThat(applicationContext.getBean(AuthFailureHandler.class)).isNotNull();
  }

  @Test
  void shouldWireSecurityFilterChains() {
    final var chains = applicationContext.getBeansOfType(SecurityFilterChain.class);
    assertThat(chains).isNotEmpty();
    assertThat(chains.size()).isGreaterThanOrEqualTo(2);
  }

  // -- JWT-protected API tests --

  @Test
  void shouldAllowRequestWithValidJwt() throws Exception {
    final String jwt = createValidJwt("test-user", "test-client", List.of("admin-group"));

    mockMvc
        .perform(get("/v1/test").header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk());
  }

  @Test
  void shouldRejectRequestWithExpiredJwt() throws Exception {
    final String jwt = createExpiredJwt("test-user", "test-client");

    mockMvc
        .perform(get("/v1/test").header("Authorization", "Bearer " + jwt))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectRequestWithInvalidSignature() throws Exception {
    // Generate a different key to create a JWT with a bad signature
    final RSAKey otherKey =
        new RSAKeyGenerator(2048)
            .keyID("other-key")
            .algorithm(JWSAlgorithm.RS256)
            .generate();

    final String issuer = wireMock.baseUrl() + "/realms/test";
    final var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("test-user")
            .audience("test-client")
            .claim("preferred_username", "test-user")
            .claim("azp", "test-client")
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .issueTime(new Date())
            .jwtID(UUID.randomUUID().toString())
            .build();

    final var signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(otherKey.getKeyID()).build(), claims);
    signedJwt.sign(new RSASSASigner(otherKey));

    mockMvc
        .perform(get("/v1/test").header("Authorization", "Bearer " + signedJwt.serialize()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectRequestWithoutAuth() throws Exception {
    mockMvc.perform(get("/v1/test")).andExpect(status().isUnauthorized());
  }

  // -- Unprotected paths --

  @Test
  void shouldAllowUnprotectedPathsWithoutAuth() throws Exception {
    // /actuator/** is in the default unprotected paths; may return 404 but never 401
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(
            result -> {
              final int statusCode = result.getResponse().getStatus();
              assertThat(statusCode).isNotEqualTo(401);
            });
  }

  // -- Claims extraction test --

  @Test
  void shouldExtractClaimsFromValidJwt() throws Exception {
    final String jwt =
        createValidJwt("claims-user", "test-client", List.of("group-a", "group-b"));

    final var result =
        mockMvc
            .perform(get("/v1/whoami").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertThat(body).contains("claims-user");
  }

  // -- Helper methods --

  private String createValidJwt(
      final String username, final String clientId, final List<String> groups) throws Exception {
    final String issuer = wireMock.baseUrl() + "/realms/test";
    final var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(username)
            .audience(clientId)
            .claim("preferred_username", username)
            .claim("azp", clientId)
            .claim("groups", groups)
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .issueTime(new Date())
            .jwtID(UUID.randomUUID().toString())
            .build();

    final var signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims);
    signedJwt.sign(new RSASSASigner(rsaKey));
    return signedJwt.serialize();
  }

  private String createExpiredJwt(final String username, final String clientId) throws Exception {
    final String issuer = wireMock.baseUrl() + "/realms/test";
    final var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(username)
            .audience(clientId)
            .claim("preferred_username", username)
            .claim("azp", clientId)
            .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
            .issueTime(Date.from(Instant.now().minusSeconds(7200)))
            .jwtID(UUID.randomUUID().toString())
            .build();

    final var signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims);
    signedJwt.sign(new RSASSASigner(rsaKey));
    return signedJwt.serialize();
  }

  // -- Test application --

  @SpringBootApplication(
      exclude = {DataSourceAutoConfiguration.class})
  static class TestApp {

    @RestController
    static class TestController {
      @GetMapping("/v1/test")
      String test() {
        return "ok";
      }

      @GetMapping("/v1/whoami")
      String whoami() {
        final var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
      }
    }
  }
}
