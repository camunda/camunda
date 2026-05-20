/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;

class PhysicalTenantRoutingFilterTest {

  private final PhysicalTenantRoutingFilter filter = new PhysicalTenantRoutingFilter();

  // ---- tenant-prefixed paths -------------------------------------------

  static Stream<Arguments> tenantPrefixedPaths() {
    return Stream.of(
        Arguments.of("/v2/physical-tenants/tenant-a/widgets", "tenant-a", "/v2/widgets"),
        Arguments.of("/v2/physical-tenants/tenant-a/widgets/123", "tenant-a", "/v2/widgets/123"),
        Arguments.of("/v2/physical-tenants/tenant-a", "tenant-a", "/v2"),
        Arguments.of(
            "/v2/physical-tenants/my-tenant/jobs/5/completion",
            "my-tenant",
            "/v2/jobs/5/completion"),
        Arguments.of("/v2/physical-tenants/my%40tenant/jobs", "my@tenant", "/v2/jobs"));
  }

  @ParameterizedTest(name = "[{index}] {0} → tenant={1}, rewritten={2}")
  @MethodSource("tenantPrefixedPaths")
  void shouldExtractTenantAndRewritePath(
      final String incomingUri, final String expectedTenantId, final String expectedUri)
      throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", incomingUri);
    final var response = mock(HttpServletResponse.class);
    final var chain = mock(FilterChain.class);
    final var captor = ArgumentCaptor.forClass(HttpServletRequest.class);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
    final HttpServletRequest forwarded = captor.getValue();

    assertThat(forwarded.getRequestURI()).isEqualTo(expectedUri);
    assertThat(forwarded.getServletPath()).isEqualTo(expectedUri);
    assertThat(forwarded.getPathInfo()).isNull();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(forwarded)).isEqualTo(expectedTenantId);
    assertThat(
            forwarded.getAttribute(
                PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED))
        .isEqualTo(true);
  }

  // ---- non-tenant paths should pass through unchanged ------------------

  static Stream<String> nonTenantPrefixedPaths() {
    return Stream.of(
        "/v2/widgets",
        "/v1/widgets",
        "/v2/physical-tenants",
        "/v2/physical-tenants/",
        "/actuator/health");
  }

  @ParameterizedTest(name = "[{index}] {0} is passed through unchanged")
  @MethodSource("nonTenantPrefixedPaths")
  void shouldPassThroughNonPrefixedRequests(final String uri) throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", uri);
    final var response = mock(HttpServletResponse.class);
    final var chain = mock(FilterChain.class);
    final var captor = ArgumentCaptor.forClass(HttpServletRequest.class);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
    final HttpServletRequest forwarded = captor.getValue();

    assertThat(forwarded.getRequestURI()).isEqualTo(uri);
    assertThat(
            forwarded.getAttribute(
                PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED))
        .isNull();
  }

  // ---- stale PATH_ATTRIBUTE must not leak through the wrapper ----------

  @Test
  void shouldHideStaleCachedPathAttributeFromWrappedRequest() throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", "/v2/physical-tenants/tenant-a/widgets");
    ServletRequestPathUtils.parseAndCache(request);
    assertThat(request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE))
        .as("precondition: stale path cached on original request")
        .isNotNull();

    final var response = mock(HttpServletResponse.class);
    final var chain = mock(FilterChain.class);
    final var captor = ArgumentCaptor.forClass(HttpServletRequest.class);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
    assertThat(captor.getValue().getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE))
        .as("stale PATH_ATTRIBUTE must not leak through the wrapper")
        .isNull();
  }

  // ---- non-empty context path ------------------------------------------

  @Test
  void shouldRewriteCorrectlyWhenContextPathIsNonEmpty() throws Exception {
    // given: non-empty context path — getRequestURI() includes it, getServletPath() does not
    final var request =
        new MockHttpServletRequest("GET", "/myapp/v2/physical-tenants/tenant-a/widgets");
    request.setContextPath("/myapp");
    final var response = mock(HttpServletResponse.class);
    final var chain = mock(FilterChain.class);
    final var captor = ArgumentCaptor.forClass(HttpServletRequest.class);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
    final HttpServletRequest forwarded = captor.getValue();
    assertThat(forwarded.getServletPath()).isEqualTo("/v2/widgets");
    assertThat(forwarded.getRequestURI()).isEqualTo("/myapp/v2/widgets");
    assertThat(PhysicalTenantContext.getPhysicalTenantId(forwarded)).isEqualTo("tenant-a");
  }
}
