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
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class OrganizationAccessCheckFilterTest {
  private FilterChain filterChain;
  private HttpServletRequest request;
  private MockHttpServletResponse response;
  private Authentication authentication;
  private Principal principal;

  @BeforeEach
  public void setUp() {
    request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    response = new MockHttpServletResponse();
    authentication = mock(Authentication.class);
    principal = mock(Principal.class);
    filterChain =
        new MockFilterChain(
            new HttpServlet() {
              @Override
              public void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
                resp.setStatus(HttpStatus.OK.value());
              }
            });
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    final SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);
  }

  @Test
  @DisplayName("should pass through if no principal is set")
  public void shouldPassThroughIfNoPrincipleSet() throws ServletException, IOException {
    // given
    final Filter filter = new OrganizationAccessCheckFilter("not-checked");

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("should return unauthorized if principal is not a CamundaPrincipal")
  public void shouldPassThroughIfPrincipalIsNotCamundaPrincipal()
      throws ServletException, IOException {
    // given
    final Filter filter = new OrganizationAccessCheckFilter("not-checked");

    // when
    when(request.getUserPrincipal()).thenReturn(principal);
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName("should pass through if principal has no organization ids")
  public void shouldPassThroughIfPrincipalHasNoOrganizationIds()
      throws ServletException, IOException {
    // given
    final var camundaUser =
        CamundaUser.CamundaUserBuilder.aCamundaUser()
            .withUsername("scooby-doo@mystery.inc")
            .withPassword("scooby-doo")
            .withName("Scooby Doo")
            .withOrganizationIds(null)
            .build();
    final Filter filter = new OrganizationAccessCheckFilter("not-checked");

    // when
    when(request.getUserPrincipal()).thenReturn(principal);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getPrincipal()).thenReturn(camundaUser);
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("should reject if principal does not have a matching organization id")
  public void shouldRejectIfPrincipalDoesNotHaveMatchingOrganizationId()
      throws ServletException, IOException {
    // given
    final var camundaUser =
        CamundaUser.CamundaUserBuilder.aCamundaUser()
            .withUsername("imposter@mystery.inc")
            .withPassword("imposter")
            .withName("Imposter")
            .withOrganizationIds(Set.of("imposter-org", "fake-org"))
            .build();
    final Filter filter = new OrganizationAccessCheckFilter("mystery-inc");

    // when
    when(request.getUserPrincipal()).thenReturn(principal);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getPrincipal()).thenReturn(camundaUser);
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName("should pass through if principal has the required organization id")
  public void shouldPassThroughIfPrincipalHasRequiredOrganizationId()
      throws ServletException, IOException {
    // given
    final var camundaUser =
        CamundaUser.CamundaUserBuilder.aCamundaUser()
            .withUsername("scooby-doo@mystery.inc")
            .withPassword("scooby-doo")
            .withName("Scooby Doo")
            .withOrganizationIds(Set.of("mystery-inc", "mystery-org"))
            .build();
    final Filter filter = new OrganizationAccessCheckFilter("mystery-inc");

    // when
    when(request.getUserPrincipal()).thenReturn(principal);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getPrincipal()).thenReturn(camundaUser);
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }
}
