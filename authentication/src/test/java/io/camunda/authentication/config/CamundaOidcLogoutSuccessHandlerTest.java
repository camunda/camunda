/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static java.time.Instant.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
public class CamundaOidcLogoutSuccessHandlerTest {

  public static final String ALLOWED_REFERER = "https://camunda.com/component/ui/page";
  @Mock ClientRegistrationRepository clientRegistrationRepository;
  private CamundaOidcLogoutSuccessHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CamundaOidcLogoutSuccessHandler(clientRegistrationRepository);
  }

  @Test
  void shouldNotSetPostLogoutRedirectWhenRefererNotAllowed() {
    // given
    final String notAllowedReferer = "https://other.com/camunda/ui/page";
    final MockHttpServletRequest request =
        buildMockHttpServletRequestWithReferer(notAllowedReferer);

    final MockHttpServletResponse response = new MockHttpServletResponse();
    final OAuth2AuthenticationToken authentication = createAuthentication("user@camunda.com");
    final ClientRegistration registration = clientRegistration();
    final HttpSession session = request.getSession(true);

    // when
    when(clientRegistrationRepository.findByRegistrationId("client")).thenReturn(registration);

    // then
    final String targetUrl = handler.determineTargetUrl(request, response, authentication);
    assertThat(session.getAttribute("postLogoutRedirect")).isNull();
    assertThat(targetUrl).contains("logout_hint=user@camunda.com");
  }

  @Test
  void shouldAppendLogoutHintToLogoutUrlWhenAvailable() {
    // given
    final MockHttpServletRequest request = buildMockHttpServletRequestWithReferer(ALLOWED_REFERER);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final OAuth2AuthenticationToken authentication = createAuthentication("user@camunda.com");

    final HttpSession session = request.getSession(true);

    // when
    when(clientRegistrationRepository.findByRegistrationId("client"))
        .thenReturn(clientRegistration());

    // then
    final String targetUrl = handler.determineTargetUrl(request, response, authentication);
    assertThat(session.getAttribute("postLogoutRedirect")).isEqualTo(ALLOWED_REFERER);
    assertThat(targetUrl).contains("logout_hint=user@camunda.com");
  }

  @Test
  void shouldReturnBaseUrlWithoutLogoutHintIfClientRegistrationIsNull() {
    // given
    final MockHttpServletRequest request = buildMockHttpServletRequestWithReferer(ALLOWED_REFERER);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final OAuth2AuthenticationToken authentication = createAuthentication("user@camunda.com");
    final HttpSession session = request.getSession(true);

    // when
    when(clientRegistrationRepository.findByRegistrationId("client")).thenReturn(null);

    // then
    final String targetUrl = handler.determineTargetUrl(request, response, authentication);
    assertThat(session.getAttribute("postLogoutRedirect")).isEqualTo(ALLOWED_REFERER);
    assertThat(targetUrl).doesNotContain("logout_hint=");
  }

  @Test
  void shouldReturnBaseUrlWithoutLogoutHintIfLogoutHintIsNull() {
    // given
    final MockHttpServletRequest request = buildMockHttpServletRequestWithReferer(ALLOWED_REFERER);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final OAuth2AuthenticationToken authentication = createAuthentication(null);
    final HttpSession session = request.getSession(true);

    // when
    when(clientRegistrationRepository.findByRegistrationId("client"))
        .thenReturn(clientRegistration());
    final String targetUrl = handler.determineTargetUrl(request, response, authentication);

    // then
    assertThat(session.getAttribute("postLogoutRedirect")).isEqualTo(ALLOWED_REFERER);
    assertThat(targetUrl).doesNotContain("logout_hint=");
  }

  @Test
  void shouldReturnsBaseUrlWhenNotOAuth2AuthenticationToken() {
    // given
    final MockHttpServletRequest request = buildMockHttpServletRequestWithReferer(ALLOWED_REFERER);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final Authentication nonOauthAuth = new UsernamePasswordAuthenticationToken("user", "password");
    final HttpSession session = request.getSession(true);

    // when
    final String targetUrl = handler.determineTargetUrl(request, response, nonOauthAuth);

    // then
    assertThat(session.getAttribute("postLogoutRedirect")).isEqualTo(ALLOWED_REFERER);
    assertThat(targetUrl).doesNotContain("logout_hint=");
  }

  @Test
  void shouldReturnsBaseUrlWhenNotOidcUser() {
    // given
    final MockHttpServletRequest request = buildMockHttpServletRequestWithReferer(ALLOWED_REFERER);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final OAuth2AuthenticationToken authentication = createOAuth2Authentication();
    final HttpSession session = request.getSession(true);

    // when
    final String targetUrl = handler.determineTargetUrl(request, response, authentication);

    // then
    assertThat(session.getAttribute("postLogoutRedirect")).isEqualTo(ALLOWED_REFERER);
    assertThat(targetUrl).doesNotContain("logout_hint=");
  }

  private static MockHttpServletRequest buildMockHttpServletRequestWithReferer(
      final String referer) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("referer", referer);
    request.setScheme("https");
    request.setServerName("camunda.com");
    request.setServerPort(443);
    request.setContextPath("/component");
    request.setRequestURI("/component/some/path");
    return request;
  }

  private OAuth2AuthenticationToken createAuthentication(final String loginHint) {
    final Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "user-id");
    if (loginHint != null) {
      claims.put("login_hint", loginHint);
    }

    final OidcIdToken token = new OidcIdToken("value", now(), now().plusSeconds(60), claims);

    final DefaultOidcUser oidcUser =
        new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token);

    return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "client");
  }

  private OAuth2AuthenticationToken createOAuth2Authentication() {
    final Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", "user-id");

    final OAuth2User user =
        new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");

    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "client");
  }

  private ClientRegistration clientRegistration() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("end_session_endpoint", "https://idp.com/logout");

    return ClientRegistration.withRegistrationId("client")
        .clientId("client-id")
        .clientSecret("client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://idp.com/oauth2/v1/authorize")
        .tokenUri("https://idp.com/oauth2/v1/token")
        .providerConfigurationMetadata(metadata)
        .build();
  }
}
