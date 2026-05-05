/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.OidcAccessTokenDecoderFactory;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
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
  @Mock private OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory;
  @InjectMocks private OidcUserAuthenticationConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
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

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn("https://issuer.example.com");
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("bar");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);

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

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn("https://issuer.example.com");
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("bar");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);

    when(jwtDecoder.decode(eq(accessTokenValue))).thenThrow(new JwtException("Failed to decode"));

    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));
    when(tokenClaimsConverter.convert(eq(idTokenClaims))).thenReturn(expectedAuthentication);

    // when
    final var userToken = authenticationConverter.convert(authentication);

    assertThat(userToken).isEqualTo(expectedAuthentication);
    verify(oidcUser).getAttributes();
    verify(tokenClaimsConverter).convert(eq(idTokenClaims));
  }

  @Test
  public void shouldPassAdditionalJwkSetUrisToDecoderFactory() {
    // given
    final var issuer = "https://issuer.example.com";
    final var additionalUris = List.of("https://issuer.example.com/extra/jwks");
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Map.of(issuer, additionalUris));

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "test-user"));

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn("test-access-token");

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn(issuer);
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("test-reg");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var jwt = mock(Jwt.class);
    final Map<String, Object> claims = Map.of("sub", "test-user");
    when(jwt.getClaims()).thenReturn(claims);
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
    when(jwtDecoder.decode(any())).thenReturn(jwt);
    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    // when
    converter.convert(authentication);

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<String>> urisCaptor = ArgumentCaptor.forClass(List.class);
    verify(oidcAccessTokenDecoderFactory)
        .createAccessTokenDecoder(eq(clientRegistration), urisCaptor.capture());
    assertThat(urisCaptor.getValue()).isEqualTo(additionalUris);
  }

  @Test
  public void shouldPassNullWhenIssuerNotInAdditionalUrisMap() {
    // given — converter configured with additional URIs for a different issuer
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Map.of("https://other-issuer.example.com", List.of("https://other/jwks")));

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "test-user"));

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn("test-access-token");

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn("https://my-issuer.example.com");
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("my-reg");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var jwt = mock(Jwt.class);
    when(jwt.getClaims()).thenReturn(Map.of("sub", "test-user"));
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
    when(jwtDecoder.decode(any())).thenReturn(jwt);
    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    // when
    converter.convert(authentication);

    // then — null is passed because issuer is not in the map
    verify(oidcAccessTokenDecoderFactory)
        .createAccessTokenDecoder(eq(clientRegistration), isNull());
  }

  @Test
  public void shouldCacheDecoderForSameClientRegistration() {
    // given — converter with additional URIs
    final var issuer = "https://issuer.example.com";
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Map.of(issuer, List.of("https://issuer.example.com/extra/jwks")));

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn(issuer);
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("cached-reg");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn("token-1", "token-2");

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "test-user"));
    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var jwt = mock(Jwt.class);
    when(jwt.getClaims()).thenReturn(Map.of("sub", "test-user"));
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
    when(jwtDecoder.decode(any())).thenReturn(jwt);
    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    // when — convert twice with the same client registration
    converter.convert(authentication);
    converter.convert(authentication);

    // then — decoder factory called only once (cached)
    verify(oidcAccessTokenDecoderFactory, times(1)).createAccessTokenDecoder(any(), any());
  }

  @Test
  public void shouldNotThrowWhenIssuerUriIsNull() {
    // given — OIDC provider configured without issuer-uri (only
    // jwkSetUri/authorizationUri/tokenUri)
    // This is the regression test for the NPE introduced by PR #47219:
    // additionalJwkSetUrisByIssuer is an immutable Map.copyOf() and does not permit null key
    // lookups.
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Map.of("https://other-issuer.example.com", List.of("https://other/jwks")));

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "test-user"));

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn("test-access-token");

    // issuerUri is null — provider configured with explicit jwkSetUri/authorizationUri/tokenUri
    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn(null);
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("no-issuer-reg");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var jwt = mock(Jwt.class);
    when(jwt.getClaims()).thenReturn(Map.of("sub", "test-user"));
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
    when(jwtDecoder.decode(any())).thenReturn(jwt);
    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    // when / then — must not throw NullPointerException when issuerUri is null
    assertThatCode(() -> converter.convert(authentication)).doesNotThrowAnyException();

    // and — null is passed to decoder factory since issuer has no additional JWKS configured
    verify(oidcAccessTokenDecoderFactory)
        .createAccessTokenDecoder(eq(clientRegistration), isNull());
  }

  @Test
  public void shouldPassNullWhenConstructedWithoutAdditionalUris() {
    // given — converter created with 4-arg constructor (no additional URIs)
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request);

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "test-user"));

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn("test-access-token");

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn("https://issuer.example.com");
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("no-additional");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var jwt = mock(Jwt.class);
    when(jwt.getClaims()).thenReturn(Map.of("sub", "test-user"));
    when(oidcAccessTokenDecoderFactory.createAccessTokenDecoder(any(), any()))
        .thenReturn(jwtDecoder);
    when(jwtDecoder.decode(any())).thenReturn(jwt);
    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("foo")));

    // when
    converter.convert(authentication);

    // then — null passed because empty map has no entry for this issuer
    verify(oidcAccessTokenDecoderFactory)
        .createAccessTokenDecoder(eq(clientRegistration), isNull());
  }

  @Test
  public void shouldUseIdTokenClaimsWhenPreferIdTokenClaimsIsEnabled() {
    // given — converter configured with prefer-id-token-claims=true for the current registration
    final var registrationId = "pingfed";
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Collections.emptyMap(),
            Map.of(registrationId, true));

    // OidcUser attributes carry the ID-token + userInfo merged claims (including the
    // userInfo-only claim)
    final var oidcUser = mock(OidcUser.class);
    final Map<String, Object> principalAttributes =
        Map.of("sub", "alice", "groups", List.of("group-a"));
    when(oidcUser.getAttributes()).thenReturn(principalAttributes);

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);
    when(authentication.getAuthorizedClientRegistrationId()).thenReturn(registrationId);

    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    // when
    converter.convert(authentication);

    // then — TokenClaimsConverter receives the principal attributes verbatim
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(tokenClaimsConverter).convert(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .containsEntry("sub", "alice")
        .containsEntry("groups", List.of("group-a"));

    // and — the access token is NOT decoded and the authorised client repository is NOT hit
    verify(jwtDecoder, never()).decode(any());
    verify(oidcAccessTokenDecoderFactory, never()).createAccessTokenDecoder(any(), any());
    verify(authorizedClientRepository, never()).loadAuthorizedClient(any(), any(), any());
  }

  @Test
  public void shouldDecodeAccessTokenWhenPreferIdTokenClaimsIsDisabledForRegistration() {
    // given — converter configured with prefer-id-token-claims=true for a DIFFERENT registration
    // than the one the authentication is for. The flag must only apply when the current
    // registration is explicitly opted in.
    final var converter =
        new OidcUserAuthenticationConverter(
            authorizedClientRepository,
            oidcAccessTokenDecoderFactory,
            tokenClaimsConverter,
            request,
            Collections.emptyMap(),
            Map.of("other-reg", true));

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "alice", "groups", List.of("group-a")));

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);
    when(authentication.getAuthorizedClientRegistrationId()).thenReturn("current-reg");

    final var accessTokenValue = "test-access-token";
    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn(accessTokenValue);

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getIssuerUri()).thenReturn("https://issuer.example.com");
    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("current-reg");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final Map<String, Object> accessTokenClaims = Map.of("sub", "alice");
    final var jwt = mock(Jwt.class);
    when(jwtDecoder.decode(eq(accessTokenValue))).thenReturn(jwt);
    when(jwt.getClaims()).thenReturn(accessTokenClaims);

    when(tokenClaimsConverter.convert(any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    // when
    converter.convert(authentication);

    // then — the access token is decoded and its claims are used (default behaviour)
    verify(jwtDecoder).decode(eq(accessTokenValue));
    verify(tokenClaimsConverter).convert(eq(accessTokenClaims));
  }
}
