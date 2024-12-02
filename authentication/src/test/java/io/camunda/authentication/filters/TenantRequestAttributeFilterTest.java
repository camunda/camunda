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
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class TenantRequestAttributeFilterTest {
  private TenantServices tenantServices;
  private UserServices userServices;

  private final UserEntity user =
      new UserEntity(100L, "u1", "Test User", "u1@camunda.test", "secret");
  private final CamundaUser camundaUser =
      new CamundaUser(user.userKey(), user.name(), user.username(), user.password());
  private final Set<String> tenantIds = Set.of("T1", "T2");
  private List<List<String>> recordedTenantIds;

  private FilterChain filterChain;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  public void setUp() {
    tenantServices = mock(TenantServices.class);
    userServices = mock(UserServices.class);
    when(tenantServices.getTenantIdsForMemberKey(user.userKey())).thenReturn(tenantIds);
    when(userServices.getUserByUsername(user.username())).thenReturn(Optional.of(user));
    request = new MockHttpServletRequest();
    request.setMethod("GET");
    response = new MockHttpServletResponse();
    recordedTenantIds = new ArrayList<>();
    filterChain =
        new MockFilterChain(
            new HttpServlet() {
              @Override
              public void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
                recordedTenantIds.add(TenantAttributeHolder.getTenantIds());
                resp.setStatus(HttpStatus.OK.value());
              }
            });
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  public void basicAuthPopulatesTenantIdsForKnownUser() throws ServletException, IOException {
    // given
    final Filter filter =
        new TenantRequestAttributeFilter(
            tenantServices, userServices, new MultiTenancyCfg().setEnabled(true));
    request.setUserPrincipal(new UsernamePasswordAuthenticationToken(camundaUser, "secret"));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(recordedTenantIds).isNotEmpty();
    assertThat(recordedTenantIds.getFirst()).containsExactlyInAnyOrderElementsOf(tenantIds);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  public void basicAuthReturnsDefaultTenantWhenMultiTenancyIsDisabled()
      throws ServletException, IOException {
    // given
    final Filter filter =
        new TenantRequestAttributeFilter(
            tenantServices, userServices, new MultiTenancyCfg().setEnabled(false));
    request.setUserPrincipal(new UsernamePasswordAuthenticationToken(camundaUser, "secret"));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(recordedTenantIds).isNotEmpty();
    assertThat(recordedTenantIds.getFirst())
        .containsExactlyInAnyOrder(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  public void basicAuthReturnsUnauthorizedForUnknownUser() throws ServletException, IOException {
    // given
    final Filter filter =
        new TenantRequestAttributeFilter(
            tenantServices, userServices, new MultiTenancyCfg().setEnabled(true));
    request.setUserPrincipal(new UsernamePasswordAuthenticationToken("unknown", "secret"));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(recordedTenantIds).isEmpty();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void returnsUnauthorizedWhenPrincipalIsNotAuthenticated()
      throws ServletException, IOException {
    // given
    final Filter filter =
        new TenantRequestAttributeFilter(
            tenantServices, userServices, new MultiTenancyCfg().setEnabled(true));
    request.setUserPrincipal(mock(Principal.class));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(recordedTenantIds).isEmpty();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }
}
