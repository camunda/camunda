/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.CertificateClientAssertionService;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.authentication.oauth.TokenResponse;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

public class CertificateBasedOAuth2FilterTest {

  @Mock private SecurityConfiguration securityConfig;
  @Mock private OidcAuthenticationConfiguration oidcConfig;
  @Mock private CertificateClientAssertionService clientAssertionService;
  @Mock private RestTemplate restTemplate;
  @Mock private JwtDecoder jwtDecoder;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private HttpSession session;

  private CertificateBasedOAuth2Filter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    when(securityConfig.getAuthentication())
        .thenReturn(mock(io.camunda.security.configuration.AuthenticationConfiguration.class));
    when(securityConfig.getAuthentication().getOidc()).thenReturn(oidcConfig);

    filter =
        new CertificateBasedOAuth2Filter(
            securityConfig, clientAssertionService, restTemplate, jwtDecoder);

    // Clear SecurityContext before each test
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldSkipFilterWhenCertificateAuthenticationDisabled() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(false);
    when(oidcConfig.getGrantType()).thenReturn("authorization_code");
    when(request.getRequestURI()).thenReturn("/v1/test");

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(clientAssertionService, never()).createClientAssertion(any(), anyString());
  }

  @Test
  void shouldSkipFilterWhenNotClientCredentialsGrantType() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType()).thenReturn("authorization_code");
    when(request.getRequestURI()).thenReturn("/v1/test");

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(clientAssertionService, never()).createClientAssertion(any(), anyString());
  }

  @Test
  void shouldPerformOAuth2FlowWhenCertificateAuthenticationEnabled() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(oidcConfig.getTokenUri())
        .thenReturn("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    when(oidcConfig.getClientId()).thenReturn("test-client-id");
    when(oidcConfig.getScope()).thenReturn(List.of("scope1", "scope2"));
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getSession(true)).thenReturn(session);

    // Mock client assertion creation
    when(clientAssertionService.createClientAssertion(eq(oidcConfig), anyString()))
        .thenReturn("test-client-assertion-jwt");

    // Mock OAuth2 token response
    final TokenResponse tokenResponse = new TokenResponse();
    tokenResponse.setAccessToken("test-access-token");
    tokenResponse.setTokenType("Bearer");
    tokenResponse.setExpiresIn(3600);

    when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
        .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

    // Mock JWT decoding
    final Jwt jwt = mock(Jwt.class);
    when(jwtDecoder.decode("test-access-token")).thenReturn(jwt);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(clientAssertionService).createClientAssertion(eq(oidcConfig), anyString());
    verify(restTemplate).postForEntity(anyString(), any(), eq(TokenResponse.class));
    verify(jwtDecoder).decode("test-access-token");
    verify(filterChain).doFilter(request, response);
    verify(session)
        .setAttribute(eq(ClientAssertionConstants.SESSION_KEY), any(Authentication.class));

    // Verify authentication was set in SecurityContext
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(auth.isAuthenticated()).isTrue();
  }

  @Test
  void shouldRestoreAuthenticationFromSession() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getSession(false)).thenReturn(session);

    // Create existing authentication in session
    final JwtAuthenticationToken existingAuth =
        new JwtAuthenticationToken(
            mock(Jwt.class),
            Collections.singletonList(
                new SimpleGrantedAuthority(ClientAssertionConstants.CERT_USER_ROLE)));
    when(session.getAttribute(ClientAssertionConstants.SESSION_KEY)).thenReturn(existingAuth);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(clientAssertionService, never()).createClientAssertion(any(), anyString());

    // Verify authentication was restored from session
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isSameAs(existingAuth);
  }

  @Test
  void shouldSkipOAuth2FlowWhenAlreadyAuthenticated() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(request.getRequestURI()).thenReturn("/v1/test");

    // Set existing authentication
    final JwtAuthenticationToken existingAuth =
        new JwtAuthenticationToken(
            mock(Jwt.class),
            Collections.singletonList(
                new SimpleGrantedAuthority(ClientAssertionConstants.CERT_USER_ROLE)));
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(clientAssertionService, never()).createClientAssertion(any(), anyString());

    // Verify existing authentication is preserved
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isSameAs(existingAuth);
  }

  @Test
  void shouldHandleOAuth2FlowFailure() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(oidcConfig.getTokenUri())
        .thenReturn("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getSession(true)).thenReturn(session);

    // Mock client assertion creation failure
    when(clientAssertionService.createClientAssertion(eq(oidcConfig), anyString()))
        .thenThrow(new RuntimeException("Certificate error"));

    doNothing().when(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void shouldUseFallbackScopeWhenNoneConfigured() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(oidcConfig.getTokenUri())
        .thenReturn("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    when(oidcConfig.getClientId()).thenReturn("test-client-id");
    when(oidcConfig.getScope()).thenReturn(null); // No scopes configured
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getSession(true)).thenReturn(session);

    // Mock client assertion creation
    when(clientAssertionService.createClientAssertion(eq(oidcConfig), anyString()))
        .thenReturn("test-client-assertion-jwt");

    // Mock OAuth2 token response
    final TokenResponse tokenResponse = new TokenResponse();
    tokenResponse.setAccessToken("test-access-token");
    tokenResponse.setTokenType("Bearer");
    tokenResponse.setExpiresIn(3600);

    when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
        .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

    // Mock JWT decoding
    final Jwt jwt = mock(Jwt.class);
    when(jwtDecoder.decode("test-access-token")).thenReturn(jwt);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(clientAssertionService).createClientAssertion(eq(oidcConfig), anyString());
    verify(filterChain).doFilter(request, response);

    // Verify authentication was set
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(auth.getAuthorities()).hasSize(1);
    assertThat(auth.getAuthorities().iterator().next().getAuthority())
        .isEqualTo(ClientAssertionConstants.CERT_USER_ROLE);
  }

  @Test
  void shouldHandleTokenResponseFailure() throws Exception {
    // given
    when(oidcConfig.isClientAssertionEnabled()).thenReturn(true);
    when(oidcConfig.getGrantType())
        .thenReturn(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE);
    when(oidcConfig.getTokenUri())
        .thenReturn("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    when(oidcConfig.getClientId()).thenReturn("test-client-id");
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getSession(true)).thenReturn(session);

    // Mock client assertion creation
    when(clientAssertionService.createClientAssertion(eq(oidcConfig), anyString()))
        .thenReturn("test-client-assertion-jwt");

    // Mock failed token response
    when(restTemplate.postForEntity(anyString(), any(), eq(TokenResponse.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));

    doNothing().when(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    verify(filterChain, never()).doFilter(request, response);
  }
}
