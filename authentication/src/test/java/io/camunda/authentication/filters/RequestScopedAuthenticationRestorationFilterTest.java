/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class RequestScopedAuthenticationRestorationFilterTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private HttpSession session;

  private RequestScopedAuthenticationRestorationFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new RequestScopedAuthenticationRestorationFilter();

    // Clear SecurityContext before each test
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldPassThroughWhenAuthenticationAlreadyExists() throws Exception {
    // given
    final Authentication existingAuth = createTestAuthentication();
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    when(request.getRequestURI()).thenReturn("/v1/test");

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isSameAs(existingAuth);
  }

  @Test
  void shouldRestoreAuthenticationFromRequestAttributes() throws Exception {
    // given
    final Authentication storedAuth = createTestAuthentication();
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getAttribute("camunda.certificate.authentication")).thenReturn(storedAuth);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isSameAs(storedAuth);
  }

  @Test
  void shouldPassThroughWhenNoAuthenticationToRestore() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getAttribute("camunda.certificate.authentication")).thenReturn(null);
    when(request.getAttribute("camunda.request.id")).thenReturn(null);
    when(request.getSession(true)).thenReturn(session);
    when(session.getId()).thenReturn("test-session-123");
    when(request.getMethod()).thenReturn("GET");
    when(request.getQueryString()).thenReturn(null);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNull();
  }

  @Test
  void shouldCreateStableRequestIdFromRequestDetails() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/v1/mapping-rules");
    when(request.getAttribute("camunda.certificate.authentication")).thenReturn(null);
    when(request.getAttribute("camunda.request.id")).thenReturn(null);
    when(request.getSession(true)).thenReturn(session);
    when(session.getId()).thenReturn("session-456");
    when(request.getMethod()).thenReturn("POST");
    when(request.getQueryString()).thenReturn("param=value");

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verify(request).setAttribute(eq("camunda.request.id"), any(String.class));
  }

  @Test
  void shouldHandleNullSessionGracefully() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getAttribute("camunda.certificate.authentication")).thenReturn(null);
    when(request.getAttribute("camunda.request.id")).thenReturn(null);
    when(request.getSession(true)).thenReturn(null);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then - should not throw exception
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldIgnoreUnauthenticatedStoredAuthentication() throws Exception {
    // given - use mock for unauthenticated auth to test the behavior
    final Authentication unauthenticatedAuth = mock(Authentication.class);
    when(unauthenticatedAuth.isAuthenticated()).thenReturn(false);

    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getAttribute("camunda.certificate.authentication"))
        .thenReturn(unauthenticatedAuth);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNull();
  }

  @Test
  void shouldHandleExceptionInAttributeRetrieval() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/v1/test");
    when(request.getAttribute("camunda.certificate.authentication"))
        .thenThrow(new RuntimeException("Attribute access error"));

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then - should not propagate exception
    verify(filterChain).doFilter(request, response);

    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNull();
  }

  private Authentication createTestAuthentication() {
    final Jwt jwt =
        Jwt.withTokenValue("test-token").header("alg", "RS256").claim("sub", "test-user").build();

    return new JwtAuthenticationToken(
        jwt, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  private static <T> T eq(T value) {
    return org.mockito.ArgumentMatchers.eq(value);
  }

  private static <T> T any(Class<T> type) {
    return org.mockito.ArgumentMatchers.any(type);
  }
}
