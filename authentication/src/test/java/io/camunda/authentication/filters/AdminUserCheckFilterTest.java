/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.RoleServices;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AdminUserCheckFilterTest {

  @Mock private final RoleServices roleServices = mock(RoleServices.class);
  @Mock private final HttpServletRequest request = mock(HttpServletRequest.class);
  @Mock private final HttpServletResponse response = mock(HttpServletResponse.class);
  @Mock private final FilterChain filterChain = mock(FilterChain.class);

  @Test
  void shouldRedirectIfNoAdminUserExistsOrIsConfigured() throws ServletException, IOException {
    // given
    final var securityConfig = new SecurityConfiguration();
    securityConfig.getAuthentication().setUnprotectedApi(false);
    when(roleServices.searchMembers(any())).thenReturn(SearchQueryResult.empty());
    when(request.getContextPath()).thenReturn("localhost:8080");
    final AdminUserCheckFilter adminUserCheckFilter =
        new AdminUserCheckFilter(securityConfig, roleServices);

    // when
    adminUserCheckFilter.doFilterInternal(request, response, filterChain);

    // then
    verify(response).sendRedirect("localhost:8080/identity/setup");
  }

  @Test
  void shouldNotRedirectIfAdminUserIsConfigured() throws ServletException, IOException {
    // given
    final var securityConfig = new SecurityConfiguration();
    securityConfig.getAuthentication().setUnprotectedApi(false);
    securityConfig
        .getInitialization()
        .getDefaultRoles()
        .put("admin", Map.of("users", Set.of("adminUser")));
    final AdminUserCheckFilter adminUserCheckFilter =
        new AdminUserCheckFilter(securityConfig, roleServices);

    // when
    adminUserCheckFilter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldNotRedirectIfAdminUserExists() throws ServletException, IOException {
    // given
    final var securityConfig = new SecurityConfiguration();
    securityConfig.getAuthentication().setUnprotectedApi(false);
    when(roleServices.searchMembers(any()))
        .thenReturn(new SearchQueryResult.Builder<RoleMemberEntity>().total(1).build());
    final AdminUserCheckFilter adminUserCheckFilter =
        new AdminUserCheckFilter(securityConfig, roleServices);

    // when
    adminUserCheckFilter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldNotRedirectIfApiIsNotProtected() throws ServletException, IOException {
    // given
    final var securityConfig = new SecurityConfiguration();
    securityConfig.getAuthentication().setUnprotectedApi(true);
    final AdminUserCheckFilter adminUserCheckFilter =
        new AdminUserCheckFilter(securityConfig, roleServices);

    // when
    adminUserCheckFilter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
  }
}
