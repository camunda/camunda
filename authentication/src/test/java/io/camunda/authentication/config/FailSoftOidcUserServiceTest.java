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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

final class FailSoftOidcUserServiceTest {

  private static final String USER_INFO_URI = "https://idp.example.com/userinfo";

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
  void shouldFallBackToIdTokenClaimsWhenUserInfoResponseIsNotReadable() {
    // given - DefaultOAuth2UserService lets this escape instead of mapping it to an
    // OAuth2AuthenticationException
    when(delegate.loadUser(any()))
        .thenThrow(
            new HttpMessageNotReadableException(
                "An error occurred reading the OAuth 2.0 Error",
                new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST)));
    final var request = oidcUserRequest("subject-1");

    // when
    final var user = service.loadUser(request);

    // then
    assertThat(user.getUserInfo()).isNull();
    assertThat(user.getIdToken().getSubject()).isEqualTo("subject-1");
  }

  /**
   * The failure from camunda/camunda#55732, end to end: a 400 with an HTML body and no
   * WWW-Authenticate header makes OAuth2ErrorResponseErrorHandler parse the HTML as OAuth2 error
   * JSON, and the parse failure used to fail the login with a 500.
   */
  @Test
  void shouldFallBackToIdTokenClaimsWhenUserInfoReturnsAnHtmlBadRequest() {
    // given
    final var userInfo =
        withBadRequest()
            .contentType(MediaType.TEXT_HTML)
            .body("<html><body><h1>400 Bad Request</h1></body></html>");

    // when/then - the userinfo call was made, failed, and the login continued on ID token claims
    assertFallsBackOn(userInfo);
  }

  /**
   * A userinfo response without the name attribute ({@code sub}) makes DefaultOAuth2UserService
   * throw IllegalArgumentException, which it doesn't map to an OAuth2AuthenticationException.
   */
  @Test
  void shouldFallBackToIdTokenClaimsWhenUserInfoLacksTheNameAttribute() {
    // given
    final var userInfo =
        withSuccess("{\"email\":\"user@example.com\"}", MediaType.APPLICATION_JSON);

    // when/then
    assertFallsBackOn(userInfo);
  }

  /** Runs the same user service setup as WebSecurityConfig#oidcUserService against userInfo. */
  private static void assertFallsBackOn(final ResponseCreator userInfo) {
    final var restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
    final var server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo(USER_INFO_URI)).andRespond(userInfo);
    final var defaultUserService = new DefaultOAuth2UserService();
    defaultUserService.setRestOperations(restTemplate);
    final var failSoft = new FailSoftOidcUserService(defaultUserService);

    final var user = failSoft.loadUser(realOidcUserRequest("subject-1"));

    server.verify();
    assertThat(user.getUserInfo()).isNull();
    assertThat(user.getIdToken().getSubject()).isEqualTo("subject-1");
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

  private static OidcUserRequest realOidcUserRequest(final String subject) {
    final var clientRegistration =
        ClientRegistration.withRegistrationId("test-provider")
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://camunda.example.com/sso-callback")
            .authorizationUri("https://idp.example.com/authorize")
            .tokenUri("https://idp.example.com/token")
            .userInfoUri(USER_INFO_URI)
            .userNameAttributeName("sub")
            .build();
    final var accessToken =
        new OAuth2AccessToken(
            TokenType.BEARER,
            "access-token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Set.of("openid", "profile", "email"));
    return new OidcUserRequest(clientRegistration, accessToken, idToken(subject));
  }

  private static OidcUserRequest oidcUserRequest(final String subject) {
    final var userInfoEndpoint = mock(ClientRegistration.ProviderDetails.UserInfoEndpoint.class);
    when(userInfoEndpoint.getUri()).thenReturn(USER_INFO_URI);

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
    return new OidcUserRequest(clientRegistration, accessToken, idToken(subject));
  }

  private static OidcIdToken idToken(final String subject) {
    return new OidcIdToken(
        "id-token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of(
            "sub", subject,
            "iss", "https://idp.example.com",
            "aud", "client-id"));
  }
}
