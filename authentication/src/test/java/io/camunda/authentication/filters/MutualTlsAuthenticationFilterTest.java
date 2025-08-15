/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.providers.MutualTlsAuthenticationProvider;
import io.camunda.authentication.service.CertificateUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class MutualTlsAuthenticationFilterTest {

  @Mock private MutualTlsAuthenticationProvider authenticationProvider;
  @Mock private CertificateUserService certificateUserService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private X509Certificate certificate;
  @Mock private Authentication existingAuth;
  @Mock private SecurityContext securityContext;

  private MutualTlsAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new MutualTlsAuthenticationFilter(authenticationProvider, certificateUserService);
    SecurityContextHolder.setContext(securityContext);
  }

  @Test
  void shouldSkipWhenAlreadyAuthenticated() throws Exception {
    // Given
    when(securityContext.getAuthentication()).thenReturn(existingAuth);
    when(existingAuth.isAuthenticated()).thenReturn(true);
    when(request.getRequestURI()).thenReturn("/test");

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authenticationProvider, never()).authenticate(any());
  }

  @Test
  void shouldSkipWhenNoCertificatesPresent() throws Exception {
    // Given
    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/test");

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authenticationProvider, never()).authenticate(any());
  }

  @Test
  void shouldSkipWhenEmptyCertificateArray() throws Exception {
    // Given
    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate"))
        .thenReturn(new X509Certificate[0]);
    when(request.getRequestURI()).thenReturn("/test");

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(filterChain).doFilter(request, response);
    verify(authenticationProvider, never()).authenticate(any());
  }

  @Test
  void shouldAuthenticateWithValidCertificate() throws Exception {
    // Given
    final X509Certificate[] certificates = {certificate};
    final var authenticatedToken =
        new PreAuthenticatedAuthenticationToken("user", certificate, List.of());
    authenticatedToken.setAuthenticated(true);

    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certificates);
    when(request.getRequestURI()).thenReturn("/test");
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(authenticationProvider.authenticate(any())).thenReturn(authenticatedToken);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(securityContext).setAuthentication(authenticatedToken);
    verify(certificateUserService).ensureUserExists(anyString(), any(X509Certificate.class));
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldHandleAuthenticationFailure() throws Exception {
    // Given
    final X509Certificate[] certificates = {certificate};

    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certificates);
    when(request.getRequestURI()).thenReturn("/test");
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(authenticationProvider.authenticate(any())).thenThrow(new RuntimeException("Auth failed"));

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(response)
        .sendError(
            HttpServletResponse.SC_UNAUTHORIZED, "Mutual TLS authentication failed: Auth failed");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void shouldContinueWhenUserServiceFails() throws Exception {
    // Given
    final X509Certificate[] certificates = {certificate};
    final var authenticatedToken =
        new PreAuthenticatedAuthenticationToken("user", certificate, List.of());
    authenticatedToken.setAuthenticated(true);

    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certificates);
    when(request.getRequestURI()).thenReturn("/test");
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(authenticationProvider.authenticate(any())).thenReturn(authenticatedToken);
    doThrow(new RuntimeException("User creation failed"))
        .when(certificateUserService)
        .ensureUserExists(anyString(), any(X509Certificate.class));

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(securityContext).setAuthentication(authenticatedToken);
    verify(filterChain).doFilter(request, response);
    // Authentication should continue despite user service failure
  }

  @Test
  void shouldHandleMissingUserService() throws Exception {
    // Given
    final MutualTlsAuthenticationFilter filterWithoutUserService =
        new MutualTlsAuthenticationFilter(authenticationProvider, null);
    final X509Certificate[] certificates = {certificate};
    final var authenticatedToken =
        new PreAuthenticatedAuthenticationToken("user", certificate, List.of());
    authenticatedToken.setAuthenticated(true);

    when(securityContext.getAuthentication()).thenReturn(null);
    when(request.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(certificates);
    when(request.getRequestURI()).thenReturn("/test");
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(authenticationProvider.authenticate(any())).thenReturn(authenticatedToken);

    // When
    filterWithoutUserService.doFilterInternal(request, response, filterChain);

    // Then
    verify(securityContext).setAuthentication(authenticatedToken);
    verify(filterChain).doFilter(request, response);
    verify(certificateUserService, never()).ensureUserExists(anyString(), any());
  }
}
