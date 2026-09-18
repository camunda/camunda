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
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.OidcUnreachableProviderAmongManyTest.ANSWERING_CLIENT_ID;
import static io.camunda.authentication.OidcUnreachableProviderAmongManyTest.SILENT_CLIENT_ID;
import static io.camunda.authentication.config.controllers.TestApiController.DEFAULT_RESPONSE;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_UNPROTECTED_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_V2_API_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * One silent identity provider must not cost the tokens of the other providers. The decoder
 * resolved every provider at the first token it read, so one outage failed every API request. The
 * library now resolves only the provider of the issuer that a token names.
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
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.providers.oidc.answering.client-id=" + ANSWERING_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.answering.client-secret=answering-secret",
      "camunda.security.authentication.providers.oidc.answering.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.providers.oidc.answering.audiences=" + ANSWERING_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.silent.client-id=" + SILENT_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.silent.client-secret=silent-secret",
      "camunda.security.authentication.providers.oidc.silent.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.providers.oidc.silent.audiences=" + SILENT_CLIENT_ID,
    })
@ActiveProfiles("consolidated-auth")
public class OidcUnreachableProviderAmongManyTest {

  static final String ANSWERING_CLIENT_ID = "camunda-answering";
  static final String SILENT_CLIENT_ID = "camunda-silent";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

  private static final String ANSWERING_REALM = "camunda-answering";
  private static final String SILENT_REALM = "camunda-silent";

  private static RSAKey signingKey;

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void registerIssuerUris(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.providers.oidc.answering.issuer-uri",
        () -> issuerUri(ANSWERING_REALM));
    registry.add(
        "camunda.security.authentication.providers.oidc.silent.issuer-uri",
        () -> issuerUri(SILENT_REALM));
  }

  @BeforeAll
  static void generateSigningKey() throws JOSEException {
    signingKey =
        new RSAKeyGenerator(2048)
            .keyID("test-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
  }

  @BeforeEach
  void serveOneProviderAndSilenceTheOther() {
    // the extension drops stub mappings between tests, and discovery happens per resolution rather
    // than at startup, so both providers are set up again for every test
    serveDiscoveryAndKeys(ANSWERING_REALM);
    stubFor(
        get(urlEqualTo(discoveryEndpoint(SILENT_REALM))).willReturn(aResponse().withStatus(500)));
  }

  @Test
  public void shouldStartWhenOneOfSeveralProvidersIsUnreachable() {
    // when an unauthenticated request hits an unprotected endpoint
    final var result = mockMvcTester.get().uri(DUMMY_UNPROTECTED_ENDPOINT).exchange();

    // then it is served, so the silent provider did not keep the context from coming up
    assertThat(result).hasStatusOk();
  }

  @Test
  public void shouldAcceptATokenOfTheProviderThatAnswers() {
    // when a token of the answering provider is presented while the other provider is silent
    final var result = callApiWith(accessTokenOf(ANSWERING_REALM, ANSWERING_CLIENT_ID));

    // then it is verified against the keys of its own issuer
    assertThat(result).hasStatusOk().hasBodyTextEqualTo(DEFAULT_RESPONSE);
  }

  /**
   * The application context caches the registration of an issuer. A separate recovery test would
   * leave this issuer resolved, and the outage test would then pass for the wrong reason.
   */
  @Test
  public void shouldFailTheTokensOfTheUnreachableProviderAndRecoverWithoutRestart() {
    // when a token of the silent provider is presented
    final var silentResult = callApiWith(accessTokenOf(SILENT_REALM, SILENT_CLIENT_ID));

    // then that request is a server error, as an outage after startup is, and not a rejected
    // credential
    assertThat(silentResult).hasStatus5xxServerError();

    // and the answering provider keeps serving its own tokens
    assertThat(callApiWith(accessTokenOf(ANSWERING_REALM, ANSWERING_CLIENT_ID)))
        .hasStatusOk()
        .hasBodyTextEqualTo(DEFAULT_RESPONSE);

    // when the silent provider answers again
    serveDiscoveryAndKeys(SILENT_REALM);

    // then the very next token of that issuer is verified — no restart needed
    assertThat(callApiWith(accessTokenOf(SILENT_REALM, SILENT_CLIENT_ID)))
        .hasStatusOk()
        .hasBodyTextEqualTo(DEFAULT_RESPONSE);
  }

  @Test
  public void shouldRejectATokenOfAnIssuerNoProviderDeclares() {
    // when a token carries an issuer that no provider declares
    final var result = callApiWith(accessTokenOf("camunda-unknown", ANSWERING_CLIENT_ID));

    // then it is a rejected credential, and not a server error: no provider is at fault
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private org.springframework.test.web.servlet.assertj.MvcTestResult callApiWith(
      final String token) {
    return mockMvcTester
        .get()
        .uri(DUMMY_V2_API_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .exchange();
  }

  private static String accessTokenOf(final String realm, final String audience) {
    final var now = new Date();
    final var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri(realm))
            .audience(audience)
            .subject("camundo")
            .claim("preferred_username", "camundo")
            .claim("scope", "openid")
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + 3600_000L))
            .build();
    final var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to sign the test token", e);
    }
    return jwt.serialize();
  }

  private static void serveDiscoveryAndKeys(final String realm) {
    stubFor(get(urlEqualTo(discoveryEndpoint(realm))).willReturn(okJson(discoveryDocument(realm))));
    stubFor(
        get(urlEqualTo(jwksEndpoint(realm)))
            .willReturn(
                okJson(
                    JSONObjectUtils.toJSONString(
                        new JWKSet(signingKey.toPublicJWK()).toJSONObject()))));
  }

  private static String discoveryEndpoint(final String realm) {
    return "/realms/" + realm + "/.well-known/openid-configuration";
  }

  private static String jwksEndpoint(final String realm) {
    return "/realms/" + realm + "/.well-known/jwks.json";
  }

  private static String issuerUri(final String realm) {
    return "http://localhost:" + wireMock.getPort() + "/realms/" + realm;
  }

  private static String discoveryDocument(final String realm) {
    return """
        {
            "issuer": "ISSUER",
            "authorization_endpoint": "ISSUER/oauth/authorize",
            "token_endpoint": "ISSUER/oauth/token",
            "userinfo_endpoint": "ISSUER/userinfo",
            "jwks_uri": "ISSUER/.well-known/jwks.json",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"]
        }
        """
        .replace("ISSUER", issuerUri(realm));
  }
}
