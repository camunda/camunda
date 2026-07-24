/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link PhysicalTenantStatusScopeFilter}. */
@ExtendWith(MockitoExtension.class)
class PhysicalTenantStatusScopeFilterTest {

  @Mock private FilterChain chain;

  private final PhysicalTenantStatusScopeFilter filter = new PhysicalTenantStatusScopeFilter();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/physical-tenants/tenanta/v2/status",
        "/physical-tenants/tenanta/v2/status/",
        "/physical-tenants/unknown/v2/status",
      })
  void shouldRejectNonDefaultTenantStatusRequestWithUniform404(final String uri) throws Exception {
    final var request = new MockHttpServletRequest("GET", uri);
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    // the body must be fixed and path-independent — sendError()/error-page dispatch would embed
    // the request path in the ProblemDetail instance, breaking the uniform-404 contract
    assertThat(response.getContentAsString())
        .isEqualTo("{\"type\":\"about:blank\",\"status\":404,\"title\":\"Not Found\"}");
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void shouldRejectExistingNonDefaultTenantIdenticallyToUnknownTenant() throws Exception {
    // both an existing non-default tenant and an unknown one must be indistinguishable — status,
    // body, and headers must all match, not just the status code
    final var existingTenantRequest =
        new MockHttpServletRequest("GET", "/physical-tenants/tenanta/v2/status");
    final var existingTenantResponse = new MockHttpServletResponse();
    final var unknownTenantRequest =
        new MockHttpServletRequest("GET", "/physical-tenants/unknown/v2/status");
    final var unknownTenantResponse = new MockHttpServletResponse();

    filter.doFilter(existingTenantRequest, existingTenantResponse, chain);
    filter.doFilter(unknownTenantRequest, unknownTenantResponse, chain);

    assertThat(existingTenantResponse.getStatus()).isEqualTo(unknownTenantResponse.getStatus());
    assertThat(existingTenantResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    assertThat(existingTenantResponse.getContentAsString())
        .isEqualTo(unknownTenantResponse.getContentAsString());
    assertThat(existingTenantResponse.getContentType())
        .isEqualTo(unknownTenantResponse.getContentType());
    assertThat(existingTenantResponse.getHeaderNames())
        .containsExactlyInAnyOrderElementsOf(unknownTenantResponse.getHeaderNames());
  }

  @Test
  void shouldAllowDefaultTenantStatusRequest() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/default/v2/status");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldAllowUnprefixedStatusRequest() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/v2/status");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldAllowOtherTenantPrefixedEndpoints() throws Exception {
    // this filter is scoped to /v2/status only; other tenant-prefixed endpoints are unaffected
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/tenanta/v2/topology");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldAllowNonPrefixedPath() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/actuator/health");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldRejectRegardlessOfContextPath() throws Exception {
    final var request =
        new MockHttpServletRequest("GET", "/zeebe/physical-tenants/tenanta/v2/status");
    request.setContextPath("/zeebe");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    verify(chain, never()).doFilter(request, response);
  }
}
