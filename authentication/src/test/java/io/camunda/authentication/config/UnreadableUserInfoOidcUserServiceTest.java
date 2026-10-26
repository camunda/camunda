/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

final class UnreadableUserInfoOidcUserServiceTest {

  private static final String USER_INFO_URI = "https://idp.example.com/userinfo";

  private MockRestServiceServer server;
  private UnreadableUserInfoOidcUserService service;

  @BeforeEach
  void setUp() {
    // the same rest client setup DefaultOAuth2UserService uses by default
    final var restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
    server = MockRestServiceServer.bindTo(restTemplate).build();
    final var defaultUserService = new DefaultOAuth2UserService();
    defaultUserService.setRestOperations(restTemplate);
    service = new UnreadableUserInfoOidcUserService(defaultUserService);
  }

  /**
   * The failure from camunda/camunda#55732: a 400 with an HTML body and no WWW-Authenticate header
   * used to escape the login filter as HttpMessageNotReadableException, a 500.
   */
  @Test
  void shouldFailLoginCleanlyWhenUserInfoReturnsAnHtmlBadRequest() {
    // given
    server
        .expect(requestTo(USER_INFO_URI))
        .andRespond(
            withBadRequest()
                .contentType(MediaType.TEXT_HTML)
                .body("<html><body><h1>400 Bad Request</h1></body></html>"));

    // when
    final var failure =
        catchThrowableOfType(
            () -> service.loadUser(oidcUserRequest()), OAuth2AuthenticationException.class);

    // then - an ordinary login failure, which the login failure handler answers with a 401
    server.verify();
    assertThat(failure.getError().getErrorCode()).isEqualTo("invalid_user_info_response");
    assertThat(failure.getError().getDescription()).doesNotContain("<html>");
    assertThat(failure).hasCauseInstanceOf(HttpMessageNotReadableException.class);
  }

  @Test
  void shouldLeaveOtherUserInfoFailuresUnchanged() {
    // given
    server.expect(requestTo(USER_INFO_URI)).andRespond(withUnauthorizedRequest());

    // when
    final var failure =
        catchThrowableOfType(
            () -> service.loadUser(oidcUserRequest()), OAuth2AuthenticationException.class);

    // then - DefaultOAuth2UserService's own mapping, as before
    assertThat(failure.getError().getErrorCode()).isEqualTo("invalid_user_info_response");
    assertThat(failure).hasCauseInstanceOf(HttpClientErrorException.class);
  }

  @Test
  void shouldLoadTheUserWhenUserInfoSucceeds() {
    // given
    server
        .expect(requestTo(USER_INFO_URI))
        .andRespond(
            withSuccess(
                "{\"sub\":\"subject-1\",\"email\":\"user@example.com\"}",
                MediaType.APPLICATION_JSON));

    // when
    final var user = service.loadUser(oidcUserRequest());

    // then
    assertThat(user.getUserInfo().getClaims()).containsEntry("email", "user@example.com");
  }

  private static OidcUserRequest oidcUserRequest() {
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
    // "profile" is in 8.8's default scopes, and makes Spring Security 6 call /userinfo
    final var accessToken =
        new OAuth2AccessToken(
            TokenType.BEARER,
            "access-token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Set.of("openid", "profile"));
    final var idToken =
        new OidcIdToken(
            "id-token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "subject-1", "iss", "https://idp.example.com", "aud", "client-id"));
    return new OidcUserRequest(clientRegistration, accessToken, idToken);
  }
}
