/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.AbstractWebSecurityConfigTest;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.authentication.converter.OidcTokenAuthenticationConverter;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.oidc.CachingOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test for the customer's reported bearer-token scenario: when a JWT does not carry the
 * configured {@code groups} claim and UserInfo augmentation is enabled, the missing {@code groups}
 * claim is obtained from the OIDC {@code /userinfo} response and merged into the claims passed to
 * {@link TokenClaimsConverter}. Authorizations that depend on {@code groups} therefore work for
 * bearer-token authentication as expected.
 *
 * <p>Also verifies the cache is consulted for repeated calls with the same token within the TTL.
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
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.groups-claim=groups",
      "camunda.security.authentication.oidc.user-info-augmentation.enabled=true",
    })
public class OidcBearerUserInfoClaimGapIT extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  @Autowired private OidcTokenAuthenticationConverter converter;
  @Autowired private OidcClaimsProvider oidcClaimsProvider;

  @MockitoBean private TokenClaimsConverter tokenClaimsConverter;

  @BeforeEach
  void resetStateBetweenTests() {
    // Reset both the shared WireMock request journal and the process-wide
    // CachingOidcClaimsProvider cache so test methods don't observe state from
    // previous tests. This lets tests choose arbitrary jti / sub values without
    // worrying about cross-test cache collisions.
    wireMock.resetRequests();
    if (oidcClaimsProvider instanceof final CachingOidcClaimsProvider caching) {
      caching.invalidateCache();
    }
  }

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/issuer/jwks");

    final var openidConfig =
        "{\"issuer\":\""
            + issuerUri
            + "\","
            + "\"token_endpoint\":\"token.example.com\","
            + "\"jwks_uri\":\"http://localhost:"
            + wireMock.getPort()
            + "/issuer/jwks\","
            + "\"userinfo_endpoint\":\""
            + issuerUri
            + "/userinfo\","
            + "\"subject_types_supported\":[\"public\"]}";
    wireMock
        .getRuntimeInfo()
        .getWireMock()
        .register(
            get(urlMatching(".*/issuer/.well-known/openid-configuration"))
                .willReturn(WireMock.jsonResponse(openidConfig, HttpStatus.OK.value())));
  }

  @Test
  void groupsFromUserInfoShouldReachTokenClaimsConverterButCurrentlyDoNot() {
    // The IdP is configured to return the groups on /userinfo (common SaaS IdP pattern).
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    // The JWT access token the customer receives does NOT carry the groups claim.
    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
            .claim("scope", "openid")
            .claim("jti", "jti-1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    converter.convert(new JwtAuthenticationToken(jwt));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(tokenClaimsConverter).convert(claimsCaptor.capture());

    // The customer's acceptance criterion: groups must arrive at TokenClaimsConverter so that
    // MembershipService.resolveMemberships(...) can grant group-based authorizations. On current
    // code this assertion fails because only JWT claims are passed through.
    assertThat(claimsCaptor.getValue())
        .containsEntry("sub", "alice")
        .containsEntry("groups", List.of("engineering"));

    // Additionally: /userinfo should have been consulted at least once. On current code it is
    // never called for bearer-token flows.
    wireMock.verify(exactly(1), getRequestedFor(urlMatching(".*/userinfo")));
  }

  @Test
  void userInfoIsCalledOnlyOncePerTokenWithinTtl() {
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    final var jwt =
        Jwt.withTokenValue("token-cached")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
            .claim("scope", "openid")
            .claim("jti", "jti-cached")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    for (int i = 0; i < 50; i++) {
      converter.convert(new JwtAuthenticationToken(jwt));
    }

    // Performance acceptance criterion: under a burst of bearer requests with the same token,
    // we must not hammer the IdP — Caffeine serves subsequent calls from cache.
    wireMock.verify(exactly(1), getRequestedFor(urlMatching(".*/userinfo")));
  }

  @Test
  void distinctTokensEachTriggerOneUserInfoCall() {
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    final Instant expiry = Instant.now().plusSeconds(3600);
    for (int i = 0; i < 3; i++) {
      final var jwt =
          Jwt.withTokenValue("token-distinct-" + i)
              .header("alg", "RS256")
              .claim("sub", "alice")
              .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
              .claim("scope", "openid")
              .claim("jti", "jti-distinct-" + i)
              .issuedAt(Instant.now())
              .expiresAt(expiry)
              .build();
      converter.convert(new JwtAuthenticationToken(jwt));
    }

    wireMock.verify(exactly(3), getRequestedFor(urlMatching(".*/userinfo")));
  }
}
