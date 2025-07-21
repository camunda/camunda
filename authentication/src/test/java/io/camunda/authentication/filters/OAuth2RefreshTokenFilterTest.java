/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.LogoutHandler;

@ExtendWith(MockitoExtension.class)
class OAuth2RefreshTokenFilterTest {
  private static final String CLIENT_REGISTRATION_ID = "test-client";
  private static final String PRINCIPAL_NAME = "test-user";
  private static final String ACCESS_TOKEN_VALUE = "access-token-123";
  private static final String REFRESH_TOKEN_VALUE = "refresh-token-456";

  @Mock(strictness = Strictness.LENIENT)
  private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @Mock(strictness = Strictness.LENIENT)
  private OAuth2AuthorizedClientManager authorizedClientManager;

  @Mock(strictness = Strictness.LENIENT)
  private LogoutHandler logoutHandler;

  @Mock(strictness = Strictness.LENIENT)
  private Supplier<SecurityContext> securityContextSupplier;

  @Mock(strictness = Strictness.LENIENT)
  private SecurityContext securityContext;

  @Mock(strictness = Strictness.LENIENT)
  private HttpServletRequest request;

  @Mock(strictness = Strictness.LENIENT)
  private HttpServletResponse response;

  @Mock(strictness = Strictness.LENIENT)
  private FilterChain filterChain;

  private OAuth2RefreshTokenFilter filter;
  private ClientRegistration clientRegistration;
  private OAuth2User oauth2User;
  private OAuth2AuthenticationToken authenticationToken;

  @BeforeEach
  void setUp() {
    filter =
        new OAuth2RefreshTokenFilter(
            authorizedClientRepository,
            authorizedClientManager,
            logoutHandler,
            securityContextSupplier);

    clientRegistration =
        ClientRegistration.withRegistrationId(CLIENT_REGISTRATION_ID)
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/sso-callback")
            .authorizationUri("http://localhost:18080/oauth/authorize")
            .tokenUri("http://localhost:18080/oauth/token")
            .build();

    oauth2User =
        new DefaultOAuth2User(
            Set.of(() -> "USER"),
            java.util.Map.of("sub", PRINCIPAL_NAME, "name", "Test User"),
            "sub");

    authenticationToken =
        new OAuth2AuthenticationToken(oauth2User, Set.of(() -> "USER"), CLIENT_REGISTRATION_ID);

    when(securityContextSupplier.get()).thenReturn(securityContext);
  }

  @Test
  void shouldContinueFilterChainWhenNotOAuth2Authentication() throws ServletException, IOException {
    // Given
    when(securityContext.getAuthentication()).thenReturn(null);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authorizedClientRepository, never()).loadAuthorizedClient(any(), any(), any());
  }

  @Test
  void shouldThrowExceptionWhenNoAuthorizedClientFound() {
    // Given
    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(null);

    // When & Then
    assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("No client could be authorized");

    assertThat(authenticationToken.isAuthenticated()).isFalse();
    verify(logoutHandler).logout(request, response, authenticationToken);
  }

  @Test
  void shouldContinueFilterChainWhenTokenNotExpired() throws ServletException, IOException {
    // Given
    final OAuth2AuthorizedClient authorizedClient = createValidAuthorizedClient();
    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(authorizedClient);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authorizedClientManager, never()).authorize(any());
  }

  @Test
  void shouldThrowExceptionWhenTokenExpiredAndNoRefreshToken() {
    // Given
    final OAuth2AuthorizedClient authorizedClient =
        createExpiredAuthorizedClientWithoutRefreshToken();
    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(authorizedClient);

    // When & Then
    assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Access token expired, refresh token does not exist");

    assertThat(authenticationToken.isAuthenticated()).isFalse();
    verify(logoutHandler).logout(request, response, authenticationToken);
  }

  @Test
  void shouldRefreshTokenSuccessfullyWhenExpiredButRefreshTokenExists()
      throws ServletException, IOException {
    // Given
    final OAuth2AuthorizedClient expiredClient = createExpiredAuthorizedClientWithRefreshToken();
    final OAuth2AuthorizedClient refreshedClient = createValidAuthorizedClient();

    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(expiredClient);
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(refreshedClient);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authorizedClientManager).authorize(any(OAuth2AuthorizeRequest.class));
  }

  @Test
  void shouldThrowExceptionWhenRefreshReturnsSameClient() {
    // Given
    final OAuth2AuthorizedClient expiredClient = createExpiredAuthorizedClientWithRefreshToken();
    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(expiredClient);
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(expiredClient);

    // When & Then
    assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(OAuth2AuthenticationException.class);

    assertThat(authenticationToken.isAuthenticated()).isFalse();
    verify(logoutHandler).logout(request, response, authenticationToken);
  }

  @Test
  void shouldThrowExceptionWhenRefreshFails() {
    // Given
    final OAuth2AuthorizedClient expiredClient = createExpiredAuthorizedClientWithRefreshToken();
    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(expiredClient);
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenThrow(new OAuth2AuthenticationException(new OAuth2Error("invalid_grant")));

    // When & Then
    assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(OAuth2AuthenticationException.class);

    assertThat(authenticationToken.isAuthenticated()).isFalse();
    verify(logoutHandler).logout(request, response, authenticationToken);
  }

  @Test
  void shouldThrowExceptionWhenRefreshedTokenIsExpired() {
    // Given
    final OAuth2AuthorizedClient expiredClient = createExpiredAuthorizedClientWithRefreshToken();
    final OAuth2AuthorizedClient stillExpiredClient =
        createExpiredAuthorizedClientWithRefreshToken();

    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(expiredClient);
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(stillExpiredClient);

    // When & Then
    assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Access token expired");

    assertThat(authenticationToken.isAuthenticated()).isFalse();
    verify(logoutHandler).logout(request, response, authenticationToken);
  }

  @Test
  void shouldReturnNullForNonOAuth2AuthenticationToken() throws ServletException, IOException {
    // Given
    final Authentication nonOAuth2Auth =
        new Authentication() {
          @Override
          public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
          }

          @Override
          public Object getCredentials() {
            return null;
          }

          @Override
          public Object getDetails() {
            return null;
          }

          @Override
          public Object getPrincipal() {
            return null;
          }

          @Override
          public boolean isAuthenticated() {
            return false;
          }

          @Override
          public void setAuthenticated(final boolean isAuthenticated)
              throws IllegalArgumentException {}

          @Override
          public String getName() {
            return "";
          }
        };

    when(securityContext.getAuthentication()).thenReturn(nonOAuth2Auth);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    assertThat(filter.getAuthenticationToken()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldDetectExpiredToken() {
    // Given
    final OAuth2AuthorizedClient expiredClient = createExpiredAuthorizedClientWithRefreshToken();
    final OAuth2AuthorizedClient validClient = createValidAuthorizedClient();

    // When & Then
    assertThat(filter.hasTokenExpired(expiredClient)).isTrue();
    assertThat(filter.hasTokenExpired(validClient)).isFalse();
  }

  @Test
  void shouldDetectRefreshToken() {
    // Given
    final OAuth2AuthorizedClient clientWithRefreshToken =
        createExpiredAuthorizedClientWithRefreshToken();
    final OAuth2AuthorizedClient clientWithoutRefreshToken =
        createExpiredAuthorizedClientWithoutRefreshToken();

    // When & Then
    assertThat(filter.existsRefreshToken(clientWithRefreshToken)).isTrue();
    assertThat(filter.existsRefreshToken(clientWithoutRefreshToken)).isFalse();
  }

  @Test
  void shouldCheckExpirationWithClockSkew() {
    final var now = Instant.now();
    // Given
    final OAuth2AccessToken expiredAlready =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(5, ChronoUnit.MINUTES),
            now.plus(30, ChronoUnit.SECONDS),
            Set.of("read", "write"));

    final OAuth2AccessToken notExpired =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(5, ChronoUnit.MINUTES),
            now.plus(90, ChronoUnit.SECONDS),
            Set.of("read", "write"));

    // When & Then
    assertThat(filter.isExpired(expiredAlready)).isTrue();
    assertThat(filter.isExpired(notExpired)).isFalse();
  }

  @Test
  void shouldCheckExpirationWithoutClockSkew() {
    final var now = Instant.now();
    // Given
    final OAuth2AccessToken notExpired =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(5, ChronoUnit.SECONDS),
            now.plus(30, ChronoUnit.SECONDS),
            Set.of("read", "write"));

    final OAuth2AccessToken expired =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(5, ChronoUnit.SECONDS),
            now.minus(1, ChronoUnit.SECONDS),
            Set.of("read", "write"));

    // When & Then
    assertThat(filter.isExpired(notExpired)).isFalse();
    assertThat(filter.isExpired(expired)).isTrue();
  }

  @Test
  void shouldHandleNullTokenExpiration() throws ServletException, IOException {
    // Given
    final OAuth2AccessToken tokenWithoutExpiration =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            Instant.now(),
            null,
            Set.of("read"));

    final OAuth2AuthorizedClient clientWithNullExpiration =
        new OAuth2AuthorizedClient(
            clientRegistration, PRINCIPAL_NAME, tokenWithoutExpiration, null);

    when(securityContext.getAuthentication()).thenReturn(authenticationToken);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(CLIENT_REGISTRATION_ID), eq(authenticationToken), any()))
        .thenReturn(clientWithNullExpiration);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    assertThat(filter.hasTokenExpired(clientWithNullExpiration)).isFalse();
    verify(filterChain).doFilter(request, response);
  }

  private OAuth2AuthorizedClient createValidAuthorizedClient() {
    final var now = Instant.now();
    final OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now,
            now.plus(1, ChronoUnit.HOURS),
            Set.of("read", "write"));

    final OAuth2RefreshToken refreshToken =
        new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, now, now.plus(30, ChronoUnit.DAYS));

    return new OAuth2AuthorizedClient(
        clientRegistration, PRINCIPAL_NAME, accessToken, refreshToken);
  }

  private OAuth2AuthorizedClient createExpiredAuthorizedClientWithRefreshToken() {
    final var now = Instant.now();
    final OAuth2AccessToken expiredAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            Set.of("read", "write"));

    final OAuth2RefreshToken refreshToken =
        new OAuth2RefreshToken(
            REFRESH_TOKEN_VALUE, now.minus(2, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

    return new OAuth2AuthorizedClient(
        clientRegistration, PRINCIPAL_NAME, expiredAccessToken, refreshToken);
  }

  private OAuth2AuthorizedClient createExpiredAuthorizedClientWithoutRefreshToken() {
    final var now = Instant.now();
    final OAuth2AccessToken expiredAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            Set.of("read", "write"));

    return new OAuth2AuthorizedClient(clientRegistration, PRINCIPAL_NAME, expiredAccessToken, null);
  }
}
