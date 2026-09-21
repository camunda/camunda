/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

final class FailSoftOidcUserServiceTest {

  @SuppressWarnings("unchecked")
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate =
      mock(OAuth2UserService.class);

  private final FailSoftOidcUserService service = new FailSoftOidcUserService(delegate);

  @Test
  void shouldFallBackToIdTokenClaimsWhenUserInfoCallFails() {
    // given
    when(delegate.loadUser(any()))
        .thenThrow(
            new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info_response", "aud mismatch", null)));
    final var request = oidcUserRequest("subject-1");

    // when
    final var user = service.loadUser(request);

    // then - login succeeds on ID token claims alone, and the delegate is never retried
    assertThat(user.getUserInfo()).isNull();
    assertThat(user.getIdToken().getSubject()).isEqualTo("subject-1");
    verify(delegate, times(1)).loadUser(any());
  }

  @Test
  void shouldPropagateSubjectMismatchAsProtocolViolation() {
    // given - the delegate call itself succeeds, but returns a foreign subject
    final var mismatchedUser =
        new DefaultOAuth2User(List.of(), Map.of("sub", "someone-else"), "sub");
    when(delegate.loadUser(any())).thenReturn(mismatchedUser);
    final var request = oidcUserRequest("subject-1");

    // when/then - OIDC Core 5.3.2 violation still fails the login
    assertThatThrownBy(() -> service.loadUser(request))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void shouldKeepRealUserInfoWhenUserInfoCallSucceeds() {
    // given
    final var matchingUser =
        new DefaultOAuth2User(
            List.of(), Map.of("sub", "subject-1", "email", "user@example.com"), "sub");
    when(delegate.loadUser(any())).thenReturn(matchingUser);
    final var request = oidcUserRequest("subject-1");

    // when
    final var user = service.loadUser(request);

    // then - unaffected: providers whose userinfo call succeeds see no change
    assertThat(user.getUserInfo()).isNotNull();
    assertThat(user.getUserInfo().getClaims()).containsEntry("email", "user@example.com");
  }

  private static OidcUserRequest oidcUserRequest(final String subject) {
    final var userInfoEndpoint = mock(ClientRegistration.ProviderDetails.UserInfoEndpoint.class);
    when(userInfoEndpoint.getUri()).thenReturn("https://idp.example.com/userinfo");

    final var providerDetails = mock(ClientRegistration.ProviderDetails.class);
    when(providerDetails.getUserInfoEndpoint()).thenReturn(userInfoEndpoint);

    final var clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("test-provider");
    when(clientRegistration.getProviderDetails()).thenReturn(providerDetails);
    when(clientRegistration.getAuthorizationGrantType())
        .thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);

    final var accessToken =
        new OAuth2AccessToken(
            TokenType.BEARER, "access-token-value", Instant.now(), Instant.now().plusSeconds(60));
    final var idToken =
        new OidcIdToken(
            "id-token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of(
                "sub", subject,
                "iss", "https://idp.example.com",
                "aud", "client-id"));
    return new OidcUserRequest(clientRegistration, accessToken, idToken);
  }
}
