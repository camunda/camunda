/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.perf;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the OIDC-protected security configuration ({@code unprotected-api=false}, OIDC bearer-token
 * auth) and prints every {@link org.springframework.security.web.SecurityFilterChain} bound to the
 * {@link FilterChainProxy}, together with the filters in order.
 *
 * <p>Pair with {@link FilterChainDumpUnprotectedTest} to diff the chain composition between the
 * protected and unprotected modes — the chain that's only in the protected case is the
 * auth-specific cost surface.
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      RestApiAuthPerfHarness.PerfTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=dump-client",
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.csrf.enabled=false",
    })
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@ActiveProfiles("consolidated-auth")
@Disabled("Run manually to capture filter-chain composition; pair with the unprotected variant.")
public class FilterChainDumpProtectedTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  private static RSAKey rsaJwk;
  private static String issuerUri;

  @Autowired private FilterChainProxy filterChainProxy;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri", () -> issuerUri + "/jwks.json");
  }

  @BeforeAll
  static void generateKeyAndStub() throws JOSEException {
    rsaJwk =
        new RSAKeyGenerator(2048)
            .keyID("dump-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    stubIdpEndpoints();
  }

  @BeforeEach
  void rePublishStubs() {
    stubIdpEndpoints();
  }

  private static void stubIdpEndpoints() {
    final var jwksBody =
        JSONObjectUtils.toJSONString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    stubFor(get(urlEqualTo("/issuer/jwks.json")).willReturn(okJson(jwksBody)));
    stubFor(
        get(urlEqualTo("/issuer/.well-known/openid-configuration"))
            .willReturn(okJson(openIdConfigBody())));
  }

  @Test
  void dumpFilterChains() {
    FilterChainDump.dump("PROTECTED (unprotected-api=false, OIDC bearer)", filterChainProxy);
  }

  private static String openIdConfigBody() {
    final var base = "http://localhost:" + wireMock.getPort() + "/issuer";
    return "{\"issuer\":\""
        + base
        + "\",\"authorization_endpoint\":\""
        + base
        + "/oauth/authorize\",\"token_endpoint\":\""
        + base
        + "/oauth/token\",\"userinfo_endpoint\":\""
        + base
        + "/userinfo\",\"jwks_uri\":\""
        + base
        + "/jwks.json\",\"response_types_supported\":[\"code\"],\"subject_types_supported\":[\"public\"],\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
  }
}
