/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.AbstractWebSecurityConfigTest;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.spring.converter.OidcTokenAuthenticationConverter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * One silent identity provider must not cost the augmented claims of the other providers, and with
 * them the authorizations that depend on the {@code groups} claim. Augmentation resolved every
 * provider at its first lookup. It now resolves the UserInfo endpoint of the issuer that a token
 * names.
 */
@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.groups-claim=groups",
      "camunda.security.authentication.oidc.user-info-augmentation.enabled=true",
      "camunda.security.authentication.providers.oidc.answering.client-id=camunda-answering",
      "camunda.security.authentication.providers.oidc.answering.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.providers.oidc.silent.client-id=camunda-silent",
      "camunda.security.authentication.providers.oidc.silent.redirect-uri=http://localhost/sso-callback",
    })
public class OidcUserInfoAugmentationPerIssuerTest extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

  private static final String ANSWERING_ISSUER_PATH = "/answering";
  private static final String SILENT_ISSUER_PATH = "/silent";

  @Autowired private OidcTokenAuthenticationConverter converter;

  @DynamicPropertySource
  static void registerIssuerUris(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.providers.oidc.answering.issuer-uri",
        () -> issuerUri(ANSWERING_ISSUER_PATH));
    registry.add(
        "camunda.security.authentication.providers.oidc.silent.issuer-uri",
        () -> issuerUri(SILENT_ISSUER_PATH));
  }

  @BeforeEach
  void serveOneProviderAndSilenceTheOther() {
    // the extension drops stub mappings between tests, and resolution happens per claims lookup
    // rather than at startup, so both providers are set up again for every test
    stubFor(
        get(urlEqualTo(discoveryEndpoint(ANSWERING_ISSUER_PATH)))
            .willReturn(okJson(discoveryDocument(ANSWERING_ISSUER_PATH))));
    stubFor(
        get(urlEqualTo(ANSWERING_ISSUER_PATH + "/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));
    stubFor(
        get(urlEqualTo(discoveryEndpoint(SILENT_ISSUER_PATH)))
            .willReturn(aResponse().withStatus(500)));
  }

  @Test
  void shouldAugmentTheTokenOfTheProviderThatAnswers() {
    // when a token of the answering provider is converted while the other provider is silent
    final CamundaAuthentication result =
        converter.convert(
            new JwtAuthenticationToken(tokenOf(ANSWERING_ISSUER_PATH, "token-answering")));

    // then its groups come from the UserInfo endpoint of its own issuer
    assertThat(result.claims())
        .containsEntry("sub", "alice")
        .containsEntry("groups", List.of("engineering"));
  }

  /**
   * The application context caches the registration of an issuer. A separate recovery test would
   * leave this issuer resolved, and the outage test would then pass for the wrong reason.
   */
  @Test
  void shouldFailTheTokensOfTheUnreachableProviderAndRecoverWithoutRestart() {
    // when a token of the silent provider is converted
    assertThatThrownBy(
            () ->
                converter.convert(
                    new JwtAuthenticationToken(tokenOf(SILENT_ISSUER_PATH, "token-silent"))))
        // then that lookup fails, and the failure names the issuer that cannot be resolved
        .hasMessageContaining(issuerUri(SILENT_ISSUER_PATH));

    // and the answering provider keeps augmenting its own tokens
    final CamundaAuthentication mixed =
        converter.convert(
            new JwtAuthenticationToken(tokenOf(ANSWERING_ISSUER_PATH, "token-mixed")));
    assertThat(mixed.claims()).containsEntry("groups", List.of("engineering"));

    // when the silent provider answers again
    stubFor(
        get(urlEqualTo(discoveryEndpoint(SILENT_ISSUER_PATH)))
            .willReturn(okJson(discoveryDocument(SILENT_ISSUER_PATH))));
    stubFor(
        get(urlEqualTo(SILENT_ISSUER_PATH + "/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"analysts\"]}")));

    // then the very next lookup of that issuer is augmented — no restart needed
    final CamundaAuthentication after =
        converter.convert(new JwtAuthenticationToken(tokenOf(SILENT_ISSUER_PATH, "token-after")));
    assertThat(after.claims()).containsEntry("groups", List.of("analysts"));
  }

  @Test
  void shouldPassATokenOfAnIssuerNoProviderDeclaresUnaugmented() {
    // when a token carries an issuer that no provider declares
    final CamundaAuthentication result =
        converter.convert(new JwtAuthenticationToken(tokenOf("/unknown", "token-unknown")));

    // then it reaches the claims converter unaugmented: augmentation has no endpoint to call, and
    // refusing such a token is the decoder's decision, not this one's
    assertThat(result.claims()).doesNotContainKey("groups");
  }

  private static Jwt tokenOf(final String issuerPath, final String tokenValue) {
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "RS256")
        .claim("sub", "alice")
        .claim("iss", issuerUri(issuerPath))
        .claim("scope", "openid")
        .claim("jti", tokenValue)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private static String discoveryEndpoint(final String issuerPath) {
    return issuerPath + "/.well-known/openid-configuration";
  }

  private static String issuerUri(final String issuerPath) {
    return "http://localhost:" + wireMock.getPort() + issuerPath;
  }

  private static String discoveryDocument(final String issuerPath) {
    return """
        {
            "issuer": "ISSUER",
            "authorization_endpoint": "ISSUER/oauth/authorize",
            "token_endpoint": "ISSUER/oauth/token",
            "userinfo_endpoint": "ISSUER/userinfo",
            "jwks_uri": "ISSUER/jwks",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"]
        }
        """
        .replace("ISSUER", issuerUri(issuerPath));
  }
}
