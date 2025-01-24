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

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.search.entities.UserEntity;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.TenantServices.TenantDTO;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class TenantRequestAttributeFilterTest {

  private final UserEntity user =
      new UserEntity(100L, "u1", "Test User", "u1@camunda.test", "secret");
  private final List<TenantDTO> tenants =
      List.of(
          new TenantDTO("T1", "Tenant 1", "Tenant 1 description"),
          new TenantDTO("T2", "Tenant 2", "Tenant 2 description"));
  private final CamundaUser camundaUser =
      CamundaUser.CamundaUserBuilder.aCamundaUser()
          .withUserKey(user.userKey())
          .withUsername(user.username())
          .withPassword(user.password())
          .withTenants(tenants)
          .withName(user.name())
          .build();

  private List<List<String>> recordedTenantIds;

  private FilterChain filterChain;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MultiTenancyConfiguration multiTenancyConfiguration;

  @BeforeEach
  public void setUp() {
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
    multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
  }

  @Test
  public void basicAuthPopulatesTenantIdsForKnownUser() throws ServletException, IOException {
    // given
    final Filter filter = new TenantRequestAttributeFilter(multiTenancyConfiguration);
    request.setUserPrincipal(new UsernamePasswordAuthenticationToken(camundaUser, "secret"));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(recordedTenantIds).isNotEmpty();
    assertThat(recordedTenantIds.getFirst())
        .containsExactlyInAnyOrderElementsOf(tenants.stream().map(TenantDTO::tenantId).toList());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  public void basicAuthReturnsDefaultTenantWhenMultiTenancyIsDisabled()
      throws ServletException, IOException {
    // given
    multiTenancyConfiguration.setEnabled(false);
    final Filter filter = new TenantRequestAttributeFilter(multiTenancyConfiguration);
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
    final Filter filter = new TenantRequestAttributeFilter(multiTenancyConfiguration);
    request.setUserPrincipal(new UsernamePasswordAuthenticationToken("unknown", "secret"));

    // when / then
    Assertions.assertThrows(
        InternalAuthenticationServiceException.class,
        () -> filter.doFilter(request, response, filterChain));

    assertThat(recordedTenantIds).isEmpty();
  }

  @Test
  public void returnsUnauthorizedWhenPrincipalIsNotAuthenticated()
      throws ServletException, IOException {
    // given
    final Filter filter = new TenantRequestAttributeFilter(multiTenancyConfiguration);
    request.setUserPrincipal(mock(Principal.class));

    // when / then
    Assertions.assertThrows(
        InternalAuthenticationServiceException.class,
        () -> filter.doFilter(request, response, filterChain));

    assertThat(recordedTenantIds).isEmpty();
  }
}
