/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.AbstractWebSecurityConfigTest;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Integration test covering the {@code prefer-id-token-claims} opt-in for an OIDC setup where a
 * specific claim only found in the userInfo response is required for mapping-rule evaluation.
 *
 * <p>When the flag is enabled, {@link OidcUserAuthenticationConverter} must short-circuit the
 * access-token decode path entirely and source claims from {@link OidcUser#getAttributes()} (which
 * Spring populates with the merged ID-token + userInfo claims during the authorisation-code flow).
 * This test exercises the full Spring bean graph (real converter, real {@link WebSecurityConfig}
 * wiring from the property through to the converter bean) to verify the flag propagates end-to-end.
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
      "camunda.security.authentication.oidc.prefer-id-token-claims=true",
    })
public class OidcUserAuthenticationConverterIntegrationTest extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  private static final String CLIENT_REGISTRATION_ID = "oidc";

  @Autowired private OidcUserAuthenticationConverter converter;

  @MockitoBean private TokenClaimsConverter tokenClaimsConverter;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/id-token/jwks");

    // Spring resolves the issuer's .well-known configuration during context startup; mock it so
    // the client registration can be built. No JWKS fetch happens in this test because the
    // prefer-id-token-claims flag short-circuits the access-token decode path entirely.
    final var openidConfig =
        "{\"issuer\": \""
            + issuerUri
            + "\","
            + "\"token_endpoint\": \"token.example.com\","
            + "\"jwks_uri\": \"http://localhost:"
            + wireMock.getPort()
            + "/id-token/jwks\","
            + "\"subject_types_supported\": [\"public\"]"
            + "}";
    wireMock
        .getRuntimeInfo()
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/issuer/.well-known/openid-configuration"))
                .willReturn(WireMock.jsonResponse(openidConfig, HttpStatus.OK.value())));
  }

  @Test
  void shouldUseIdTokenAndUserInfoClaimsWhenPreferIdTokenClaimsIsEnabled() {
    // given — a logged-in OidcUser whose principal attributes carry the ID-token + userInfo
    // merged claims (which is where a userInfo-only claim lives once Spring fetches userInfo
    // during the authorisation-code flow).
    final var oidcUser = Mockito.mock(OidcUser.class);
    Mockito.when(oidcUser.getAttributes())
        .thenReturn(Map.of("sub", "alice", "groups", List.of("group-a")));

    final var authentication =
        new OAuth2AuthenticationToken(oidcUser, List.of(), CLIENT_REGISTRATION_ID);

    // the converter's request-scoped HttpServletRequest proxy resolves via RequestContextHolder;
    // bind a mock request for the duration of the call.
    final var request = new MockHttpServletRequest();
    final var response = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    try {
      // when — the converter runs through the real WebSecurityConfig bean graph. With
      // prefer-id-token-claims=true, it must skip access-token decoding and return the
      // principal attributes directly.
      converter.convert(authentication);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }

    // then — TokenClaimsConverter receives the userInfo-merged principal attributes,
    // including the userInfo-only claim that mapping rules need to match against.
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    Mockito.verify(tokenClaimsConverter).convert(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .containsEntry("sub", "alice")
        .containsEntry("groups", List.of("group-a"));
  }
}
