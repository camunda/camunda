/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class WebComponentAuthorizationCheckFilterTest {

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private CamundaAuthentication authentication;

  private WebComponentAuthorizationCheckFilter filter;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    final var securityConfig = new SecurityConfiguration();
    securityConfig.getAuthorizations().setEnabled(true);

    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);

    filter =
        new WebComponentAuthorizationCheckFilter(
            securityConfig, authenticationProvider, resourceAccessProvider);

    when(request.getContextPath()).thenReturn("");

    final var mockAuth = mock(Authentication.class);
    when(mockAuth.isAuthenticated()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(mockAuth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldAllowAccessToAdminWithIdentityPermission() throws ServletException, IOException {
    // given
    when(request.getRequestURI()).thenReturn("/admin/user");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/admin/user"));

    final var identityAccess = mock(ResourceAccess.class);
    when(identityAccess.allowed()).thenReturn(true);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("identity")))
        .thenReturn(identityAccess);

    final var adminAccess = mock(ResourceAccess.class);
    when(adminAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("admin")))
        .thenReturn(adminAccess);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldAllowAccessToIdentityWithAdminPermission() throws ServletException, IOException {
    // given
    when(request.getRequestURI()).thenReturn("/identity/user");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/identity/user"));

    final var adminAccess = mock(ResourceAccess.class);
    when(adminAccess.allowed()).thenReturn(true);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("admin")))
        .thenReturn(adminAccess);

    final var identityAccess = mock(ResourceAccess.class);
    when(identityAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("identity")))
        .thenReturn(identityAccess);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldDenyAccessToAdminWithoutEitherPermission() throws ServletException, IOException {
    // given
    when(request.getRequestURI()).thenReturn("/admin/user");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/admin/user"));

    final var identityAccess = mock(ResourceAccess.class);
    when(identityAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("identity")))
        .thenReturn(identityAccess);

    final var adminAccess = mock(ResourceAccess.class);
    when(adminAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("admin")))
        .thenReturn(adminAccess);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(response).sendRedirect("/admin/forbidden");
  }

  @Test
  void shouldDenyAccessToIdentityWithoutEitherPermission() throws ServletException, IOException {
    // given
    when(request.getRequestURI()).thenReturn("/identity/user");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/identity/user"));

    final var identityAccess = mock(ResourceAccess.class);
    when(identityAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("identity")))
        .thenReturn(identityAccess);

    final var adminAccess = mock(ResourceAccess.class);
    when(adminAccess.allowed()).thenReturn(false);
    when(resourceAccessProvider.hasResourceAccessByResourceId(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION), eq("admin")))
        .thenReturn(adminAccess);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(response).sendRedirect("/identity/forbidden");
  }
}
