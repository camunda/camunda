/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * CSL supplies the {@code CamundaAuthenticationConverter} implementations but registers none of
 * them as beans, so the host must. Without a converter for the token type at hand, {@code
 * CamundaSpringAuthenticationDelegatingConverter} throws and the request fails with a 500 rather
 * than resolving the current user.
 */
@ExtendWith(MockitoExtension.class)
class OptimizeCamundaSecurityConfigTest {

  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private OidcAccessTokenDecoderFactory accessTokenDecoderFactory;
  @Mock private LazyTokenClaimsConverter tokenClaimsConverter;
  @Mock private HttpServletRequest request;
  @Mock private OidcClaimsProvider oidcClaimsProvider;

  private final OptimizeCamundaSecurityConfig config = new OptimizeCamundaSecurityConfig();

  @Test
  void shouldConvertACslLoginSession() {
    final CamundaAuthenticationConverter<Authentication> converter =
        config.oidcUserAuthenticationConverter(
            authorizedClientRepository, accessTokenDecoderFactory, tokenClaimsConverter, request);

    assertThat(converter.supports(oauth2Session())).isTrue();
  }

  @Test
  void shouldConvertABearerToken() {
    final CamundaAuthenticationConverter<Authentication> converter =
        config.oidcTokenAuthenticationConverter(tokenClaimsConverter, oidcClaimsProvider);

    assertThat(converter.supports(new JwtAuthenticationToken(bearerJwt()))).isTrue();
  }

  private static OAuth2AuthenticationToken oauth2Session() {
    final OidcIdToken idToken =
        new OidcIdToken(
            "id-token", Instant.now(), Instant.now().plusSeconds(300), Map.of("sub", "auth0|user"));
    return new OAuth2AuthenticationToken(
        new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken),
        List.of(),
        "oidc");
  }

  private static Jwt bearerJwt() {
    return Jwt.withTokenValue("token").header("alg", "none").claim("sub", "auth0|user").build();
  }
}
