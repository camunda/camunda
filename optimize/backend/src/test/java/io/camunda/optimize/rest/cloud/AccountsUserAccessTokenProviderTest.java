/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AccountsUserAccessTokenProviderTest {

  @Mock private ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider;
  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private OAuth2AuthorizedClient authorizedClient;
  @Mock private OAuth2AccessToken accessToken;
  @Mock private HttpServletRequest request;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  private AccountsUserAccessTokenProvider provider() {
    return new AccountsUserAccessTokenProvider(authorizedClientRepositoryProvider);
  }

  private void currentRequestWithoutServiceCookie() {
    when(request.getCookies()).thenReturn(new Cookie[0]);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private void authenticateWith(
      final org.springframework.security.core.Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private OAuth2AuthenticationToken oauthToken() {
    final OAuth2User user =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            java.util.Map.of("sub", "user"),
            "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "auth0");
  }

  @Test
  void resolvesAccessTokenFromOidcSessionForOAuth2Authentication() {
    currentRequestWithoutServiceCookie();
    final OAuth2AuthenticationToken token = oauthToken();
    authenticateWith(token);
    when(authorizedClientRepositoryProvider.getIfAvailable())
        .thenReturn(authorizedClientRepository);
    when(authorizedClientRepository.loadAuthorizedClient(eq("auth0"), eq(token), eq(request)))
        .thenReturn(authorizedClient);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(accessToken.getTokenValue()).thenReturn("csl-access-token");

    assertThat(provider().getCurrentUsersAccessToken()).contains("csl-access-token");
  }

  @Test
  void returnsEmptyForOAuth2AuthenticationWhenNoAuthorizedClientRepositoryIsPresent() {
    currentRequestWithoutServiceCookie();
    authenticateWith(oauthToken());
    when(authorizedClientRepositoryProvider.getIfAvailable()).thenReturn(null);

    assertThat(provider().getCurrentUsersAccessToken()).isEmpty();
  }

  @Test
  void stillResolvesBearerTokenFromJwtAuthentication() {
    currentRequestWithoutServiceCookie();
    final Jwt jwt =
        Jwt.withTokenValue("bearer-token").header("alg", "none").claim("sub", "user").build();
    authenticateWith(new JwtAuthenticationToken(jwt));

    assertThat(provider().getCurrentUsersAccessToken()).contains("bearer-token");
  }
}
