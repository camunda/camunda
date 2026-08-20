/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link PhysicalTenantFilter}. */
@ExtendWith(MockitoExtension.class)
class PhysicalTenantFilterTest {

  @Mock private FilterChain chain;

  private final PhysicalTenantFilter filter = new PhysicalTenantFilter();

  @Test
  void shouldStampPhysicalTenantIdForKnownPrefixedPath() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/tenanta/v2/users");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenanta");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldStampPhysicalTenantIdWhenServletContextPathIsConfigured() throws Exception {
    // getRequestURI() includes the context path; the filter must strip it so the tenant id is still
    // extracted when the host is deployed under a context path (e.g. server.servlet.context-path).
    final var request =
        new MockHttpServletRequest("GET", "/zeebe/physical-tenants/tenanta/v2/users");
    request.setContextPath("/zeebe");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenanta");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldStampPhysicalTenantIdForUnknownPrefixedPath() throws Exception {
    // The filter does not validate against configured ids — CSL's chain handles rejection.
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/unknown/v2/users");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("unknown");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldNotStampAttributeForNonPrefixedPath() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/v2/users");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    // Attribute not set — getPhysicalTenantId returns null; only current() falls back to default.
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldAlwaysPassRequestDownTheChain() throws Exception {
    // Even when no tenant id is extracted, the filter must not abort the chain.
    final var request = new MockHttpServletRequest("GET", "/actuator/health");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldExtractTenantIdBeforeTrailingPathSegments() throws Exception {
    final var request =
        new MockHttpServletRequest(
            "GET", PHYSICAL_TENANTS_PATH_SEGMENT + "tenantb/some/deep/path?q=1");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenantb");
  }

  @Test
  void shouldNotStampAttributeWhenPathPrefixHasNoIdSegment() throws Exception {
    // Path is exactly /physical-tenants/ — no id segment follows.
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    // Attribute not set — getPhysicalTenantId returns null; only current() falls back to default.
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isNull();
    verify(chain).doFilter(request, response);
  }
}
