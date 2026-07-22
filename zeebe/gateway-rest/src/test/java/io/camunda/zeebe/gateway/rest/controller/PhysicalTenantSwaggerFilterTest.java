/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link PhysicalTenantSwaggerFilter}. */
@ExtendWith(MockitoExtension.class)
class PhysicalTenantSwaggerFilterTest {

  @Mock private FilterChain chain;

  private final PhysicalTenantSwaggerFilter filter = new PhysicalTenantSwaggerFilter();

  @Test
  void shouldRedirectBareSwaggerPathToTenantPrefixedIndex() throws Exception {
    assertRedirectsToIndex("/physical-tenants/tenanta/swagger");
  }

  @Test
  void shouldRedirectBareSwaggerPathWithTrailingSlash() throws Exception {
    assertRedirectsToIndex("/physical-tenants/tenanta/swagger/");
  }

  @Test
  void shouldRedirectBareSwaggerUiPathToTenantPrefixedIndex() throws Exception {
    assertRedirectsToIndex("/physical-tenants/tenanta/swagger-ui");
  }

  @Test
  void shouldRedirectBareSwaggerUiPathWithTrailingSlash() throws Exception {
    assertRedirectsToIndex("/physical-tenants/tenanta/swagger-ui/");
  }

  private void assertRedirectsToIndex(final String uri) throws Exception {
    final var request = new MockHttpServletRequest("GET", uri);
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("/physical-tenants/tenanta/swagger-ui/index.html");
    verifyNoInteractions(chain);
  }

  @Test
  void shouldForwardSwaggerUiIndexAssetToUnprefixedHandler() throws Exception {
    final var request =
        new MockHttpServletRequest("GET", "/physical-tenants/tenanta/swagger-ui/index.html");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getForwardedUrl()).isEqualTo("/swagger-ui/index.html");
    assertThat(response.getRedirectedUrl()).isNull();
    verifyNoInteractions(chain);
  }

  @Test
  void shouldForwardSwaggerUiBundleAssetToUnprefixedHandler() throws Exception {
    final var request =
        new MockHttpServletRequest(
            "GET", "/physical-tenants/tenanta/swagger-ui/swagger-ui-bundle.js");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getForwardedUrl()).isEqualTo("/swagger-ui/swagger-ui-bundle.js");
    assertThat(response.getRedirectedUrl()).isNull();
    verifyNoInteractions(chain);
  }

  @Test
  void shouldNotMatchPathThatOnlySharesSwaggerUiPrefix() throws Exception {
    // "/swagger-uiFoo" must not be treated as a swagger-ui asset request
    final var request =
        new MockHttpServletRequest("GET", "/physical-tenants/tenanta/swagger-uiFoo");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getForwardedUrl()).isNull();
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldPassThroughUnrelatedTenantPrefixedPath() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/physical-tenants/tenanta/v2/widgets");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldPassThroughWhenNoTenantIdStamped() throws Exception {
    final var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getForwardedUrl()).isNull();
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldRedirectWithServletContextPathPrefixed() throws Exception {
    final var request =
        new MockHttpServletRequest("GET", "/zeebe/physical-tenants/tenanta/swagger");
    request.setContextPath("/zeebe");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("/zeebe/physical-tenants/tenanta/swagger-ui/index.html");
    assertThat(response.getForwardedUrl()).isNull();
    verifyNoInteractions(chain);
  }
}
