/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class OidcUserAuthenticationConverterTest {

  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private JwtDecoder jwtDecoder;
  @Mock private TokenClaimsConverter tokenClaimsConverter;
  @Mock private HttpServletRequest request;
  @InjectMocks private OidcUserAuthenticationConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
  }

  @Test
  void shouldSupport() {
    // given
    final var authentication = mock(OAuth2AuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupport() {
    // given
    final var authentication = mock(JwtAuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isFalse();
  }

  @Test
  public void shouldConvertAccessToken() {
    // given
    final var oidcUser = mock(OidcUser.class);
    final Map<String, Object> idTokenClaims =
        Map.of("id_token", "test-id-token", "token_type", "ID", "expires_in", 3600);
    when(oidcUser.getAttributes()).thenReturn(idTokenClaims);

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessTokenValue = "test-access-token";
    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn(accessTokenValue);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final Map<String, Object> accessTokenClaims =
        Map.of("access_token", "test-access-token", "token_type", "Bearer", "expires_in", 3600);
    final var jwt = mock(Jwt.class);
    when(jwtDecoder.decode(eq(accessTokenValue))).thenReturn(jwt);
    when(jwt.getClaims()).thenReturn(accessTokenClaims);

    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));
    when(tokenClaimsConverter.convert(eq(accessTokenClaims))).thenReturn(expectedAuthentication);

    // when
    final var userToken = authenticationConverter.convert(authentication);

    assertThat(userToken).isEqualTo(expectedAuthentication);
    verify(oidcUser, times(0)).getAttributes();
    verify(tokenClaimsConverter).convert(eq(accessTokenClaims));
  }

  @Test
  public void shouldFallbackToIdToken() {
    // given
    final var oidcUser = mock(OidcUser.class);
    final Map<String, Object> idTokenClaims =
        Map.of("id_token", "test-id-token", "token_type", "ID", "expires_in", 3600);
    when(oidcUser.getAttributes()).thenReturn(idTokenClaims);

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any())).thenReturn(null);

    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));
    when(tokenClaimsConverter.convert(eq(idTokenClaims))).thenReturn(expectedAuthentication);

    // when
    final var userToken = authenticationConverter.convert(authentication);

    assertThat(userToken).isEqualTo(expectedAuthentication);
    verify(oidcUser).getAttributes();
    verify(tokenClaimsConverter).convert(eq(idTokenClaims));
  }

  @Test
  public void shouldFallbackToIdTokenWhenAccessTokenDecodingFails() {
    // given
    final var oidcUser = mock(OidcUser.class);
    final Map<String, Object> idTokenClaims =
        Map.of("id_token", "test-id-token", "token_type", "ID", "expires_in", 3600);
    when(oidcUser.getAttributes()).thenReturn(idTokenClaims);

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessTokenValue = "test-access-token";
    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn(accessTokenValue);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    when(jwtDecoder.decode(eq(accessTokenValue))).thenThrow(new JwtException("Failed to decode"));

    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));
    when(tokenClaimsConverter.convert(eq(idTokenClaims))).thenReturn(expectedAuthentication);

    // when
    final var userToken = authenticationConverter.convert(authentication);

    assertThat(userToken).isEqualTo(expectedAuthentication);
    verify(oidcUser).getAttributes();
    verify(tokenClaimsConverter).convert(eq(idTokenClaims));
  }
}
