/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2RefreshTokenFilterTest {

  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private OAuth2AuthorizedClientManager authorizedClientManager;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  private OAuth2RefreshTokenFilter filter;

  @BeforeEach
  void setUp() {
    // Fixed clock at epoch + 1000 seconds
    final Clock fixedClock =
        Clock.fixed(Instant.ofEpochSecond(1000), ZoneId.of("UTC"));
    filter =
        new OAuth2RefreshTokenFilter(
            authorizedClientRepository,
            authorizedClientManager,
            Duration.ofSeconds(60),
            fixedClock);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldContinueFilterChainForNonOAuth2Authentication() throws Exception {
    // given
    SecurityContextHolder.clearContext();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(authorizedClientRepository, never()).loadAuthorizedClient(any(), any(), any());
  }

  @Test
  void shouldContinueFilterChainWhenTokenNotExpired() throws Exception {
    // given
    final OAuth2AuthenticationToken authToken = createAuthToken("my-reg");
    SecurityContextHolder.getContext().setAuthentication(authToken);

    final OAuth2AuthorizedClient client = createAuthorizedClient("my-reg", Instant.ofEpochSecond(2000));
    when(authorizedClientRepository.loadAuthorizedClient("my-reg", authToken, request))
        .thenReturn(client);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(authorizedClientManager, never()).authorize(any());
  }

  @Test
  void shouldRefreshTokenWhenExpired() throws Exception {
    // given
    final OAuth2AuthenticationToken authToken = createAuthToken("my-reg");
    SecurityContextHolder.getContext().setAuthentication(authToken);

    // Token expires at epoch+900, clock is at epoch+1000, with 60s skew this is expired
    final OAuth2AuthorizedClient expiredClient =
        createAuthorizedClient("my-reg", Instant.ofEpochSecond(900));
    when(authorizedClientRepository.loadAuthorizedClient("my-reg", authToken, request))
        .thenReturn(expiredClient);

    final OAuth2AuthorizedClient refreshedClient =
        createAuthorizedClient("my-reg", Instant.ofEpochSecond(2000));
    when(authorizedClientManager.authorize(any())).thenReturn(refreshedClient);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(authorizedClientManager).authorize(any());
    verify(authorizedClientRepository)
        .saveAuthorizedClient(eq(refreshedClient), eq(authToken), eq(request), eq(response));
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldLogoutWhenRefreshReturnsNull() throws Exception {
    // given
    final OAuth2AuthenticationToken authToken = createAuthToken("my-reg");
    SecurityContextHolder.getContext().setAuthentication(authToken);

    final OAuth2AuthorizedClient expiredClient =
        createAuthorizedClient("my-reg", Instant.ofEpochSecond(900));
    when(authorizedClientRepository.loadAuthorizedClient("my-reg", authToken, request))
        .thenReturn(expiredClient);
    when(authorizedClientManager.authorize(any())).thenReturn(null);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain, never()).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private OAuth2AuthenticationToken createAuthToken(final String registrationId) {
    final OAuth2User user =
        new DefaultOAuth2User(
            java.util.List.of(),
            java.util.Map.of("sub", "user1"),
            "sub");
    return new OAuth2AuthenticationToken(user, java.util.List.of(), registrationId);
  }

  private OAuth2AuthorizedClient createAuthorizedClient(
      final String registrationId, final Instant expiresAt) {
    final ClientRegistration registration =
        ClientRegistration.withRegistrationId(registrationId)
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/callback")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .build();

    final OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token-value",
            Instant.ofEpochSecond(0),
            expiresAt);

    return new OAuth2AuthorizedClient(
        registration,
        "user1",
        accessToken);
  }
}
