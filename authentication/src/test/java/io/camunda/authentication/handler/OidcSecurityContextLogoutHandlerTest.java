/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.OidcClientRegistration;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcSecurityContextLogoutHandlerTest {

  @Mock private ClientRegistrationRepository clientRegistrationRepository;
  @Mock private OAuth2AuthorizedClientService authorizedClientService;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private AuthenticationConfiguration authenticationConfig;
  @Mock private OidcAuthenticationConfiguration oidcConfig;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private HttpSession session;
  @Mock private OAuth2AuthenticationToken oauth2AuthenticationToken;
  @Mock private OAuth2AuthorizedClient authorizedClient;
  @Mock private ClientRegistration clientRegistration;
  @Mock private OAuth2AccessToken accessToken;
  @Mock private OAuth2RefreshToken refreshToken;
  @Mock private RestTemplate restTemplate;

  private OidcSecurityContextLogoutHandler logoutHandler;

  @BeforeEach
  void setUp() {
    logoutHandler =
        new OidcSecurityContextLogoutHandler(
            clientRegistrationRepository, authorizedClientService, securityConfiguration);

    when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfig);
    when(authenticationConfig.getOidc()).thenReturn(oidcConfig);
  }

  @Test
  void shouldDoNothingWhenAuthenticationIsNull() {
    // Given
    when(request.getSession(false)).thenReturn(session);

    // When
    logoutHandler.logout(request, response, null);

    // Then
    verifyNoInteractions(session);
    verifyNoInteractions(authorizedClientService);
  }

  @Test
  void shouldOnlyClearContextAndSessionWithNonOAuth2Authentication() {
    // Given
    final Authentication regularAuth = mock(Authentication.class);
    when(request.getSession(false)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, regularAuth);

      // Then
      verify(session).invalidate();
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
      verifyNoInteractions(authorizedClientService);
      verifyNoInteractions(restTemplate);
    }
  }

  @Test
  void shouldStillClearContextAndSessionWithNoAuthorizedClient() {
    // Given
    final String registrationId = "test-registration";
    final String principalName = "test-user";

    when(oauth2AuthenticationToken.getAuthorizedClientRegistrationId()).thenReturn(registrationId);
    when(oauth2AuthenticationToken.getName()).thenReturn(principalName);
    when(authorizedClientService.loadAuthorizedClient(registrationId, principalName))
        .thenReturn(null);
    when(request.getSession(false)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, oauth2AuthenticationToken);

      // Then
      verify(authorizedClientService).removeAuthorizedClient(registrationId, principalName);
      verify(session).invalidate();
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
      verifyNoInteractions(restTemplate);
    }
  }

  @Test
  void shouldContinueWithLogoutWithTokenRevocationFailure() {
    // Given
    final String registrationId = "test-registration";
    final String principalName = "test-user";
    final String revokeUri = "https://provider.com/revoke";

    when(oauth2AuthenticationToken.getAuthorizedClientRegistrationId()).thenReturn(registrationId);
    when(oauth2AuthenticationToken.getName()).thenReturn(principalName);
    when(authorizedClientService.loadAuthorizedClient(registrationId, principalName))
        .thenReturn(authorizedClient);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClient.getRefreshToken()).thenReturn(refreshToken);
    when(clientRegistration.getClientId()).thenReturn("client-id");
    when(clientRegistration.getClientSecret()).thenReturn("client-secret");
    when(accessToken.getTokenValue()).thenReturn("access-token");
    when(refreshToken.getTokenValue()).thenReturn("refresh-token");
    when(request.getSession(false)).thenReturn(session);

    when(restTemplate.postForEntity(eq(revokeUri), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new RestClientException("Network error"));

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, oauth2AuthenticationToken);

      // Then
      verify(authorizedClientService).removeAuthorizedClient(registrationId, principalName);
      verify(session).invalidate();
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
    }
  }

  @Test
  void shouldNotThrowExceptionWithNoSession() {
    // Given
    when(request.getSession(false)).thenReturn(null);

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      assertDoesNotThrow(() -> logoutHandler.logout(request, response, oauth2AuthenticationToken));

      // Then
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
    }
  }

  @Test
  void shouldSkipTokenRevocationWithNullRevokeUri() {
    // Given
    final String registrationId = "test-registration";
    final String principalName = "test-user";

    when(oauth2AuthenticationToken.getAuthorizedClientRegistrationId()).thenReturn(registrationId);
    when(oauth2AuthenticationToken.getName()).thenReturn(principalName);
    when(authorizedClientService.loadAuthorizedClient(registrationId, principalName))
        .thenReturn(authorizedClient);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(request.getSession(false)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, oauth2AuthenticationToken);

      // Then
      verify(authorizedClientService).removeAuthorizedClient(registrationId, principalName);
      verifyNoInteractions(restTemplate);
      verify(session).invalidate();
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
    }
  }

  @Test
  void shouldContinueWithHttpErrorResponse() {
    // Given
    final String registrationId = "test-registration";
    final String principalName = "test-user";
    final String revokeUri = "https://provider.com/revoke";

    when(oauth2AuthenticationToken.getAuthorizedClientRegistrationId()).thenReturn(registrationId);
    when(oauth2AuthenticationToken.getName()).thenReturn(principalName);
    when(authorizedClientService.loadAuthorizedClient(registrationId, principalName))
        .thenReturn(authorizedClient);
    when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(clientRegistration.getClientId()).thenReturn("client-id");
    when(clientRegistration.getClientSecret()).thenReturn("client-secret");
    when(accessToken.getTokenValue()).thenReturn("access-token");
    when(request.getSession(false)).thenReturn(session);

    when(restTemplate.postForEntity(eq(revokeUri), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("Error", HttpStatus.BAD_REQUEST));

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, oauth2AuthenticationToken);

      // Then
      verify(authorizedClientService).removeAuthorizedClient(registrationId, principalName);
      verify(session).invalidate();
      mockedSecurityContext.verify(SecurityContextHolder::clearContext);
    }
  }

  @Test
  void shouldCreateCorrectUrlWithValidConfiguration() {
    // Given
    final String logoutUri = "https://provider.com/logout";
    final String clientId = "test-client-id";

    when(clientRegistrationRepository.findByRegistrationId(OidcClientRegistration.REGISTRATION_ID))
        .thenReturn(clientRegistration);
    when(clientRegistration.getClientId()).thenReturn(clientId);
    when(oidcConfig.getIssuerLogoutUrl()).thenReturn(logoutUri);
    when(request.getSession(false)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mockedSecurityContext =
        mockStatic(SecurityContextHolder.class)) {

      // When
      logoutHandler.logout(request, response, oauth2AuthenticationToken);

      // Then
      verify(request)
          .setAttribute(
              eq(OidcLogoutSuccessHandler.LOGOUT_SUCCESS_REDIRECT_URL_PROPERTY),
              eq(logoutUri + "?client_id=" + clientId));
    }
  }
}
