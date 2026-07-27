/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

/**
 * Integration test proving {@code /v2/status} is scoped to the default physical tenant end-to-end,
 * through the real filter registration/order in {@link ApiFiltersConfiguration} — not just the
 * {@link PhysicalTenantStatusScopeFilter} class in isolation (covered by {@link
 * PhysicalTenantStatusScopeFilterTest}).
 *
 * <p>Guards against a regression where the filter is registered at the wrong order (or not at all)
 * and a non-default {@code /physical-tenants/{id}/v2/status} request reaches {@link
 * StatusController} instead of getting rejected upfront. This {@code @WebMvcTest} slice does not
 * load Spring Security, so it cannot itself prove the filter runs ahead of a per-tenant security
 * chain; instead {@link #shouldRegisterStatusScopeFilterBeforeSpringSecuritysFilterChainProxy}
 * asserts the {@link FilterRegistrationBean} order directly against {@code
 * SecurityProperties.DEFAULT_FILTER_ORDER} ({@code -100}), so a future order regression is caught
 * even without a security context. See ADR 001 D3.
 */
@WebMvcTest(StatusController.class)
@Import(ApiFiltersConfiguration.class)
class PhysicalTenantStatusScopeIT extends RestControllerTest {

  // Spring Security's FilterChainProxy registers at SecurityProperties.DEFAULT_FILTER_ORDER
  // (-100); mirrors the constant inlined in ApiFiltersConfiguration for the same reason (avoids a
  // spring-boot-security dependency for one constant).
  private static final int SPRING_SECURITY_DEFAULT_FILTER_ORDER = -100;

  @MockitoBean TopologyServices topologyServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @Autowired
  FilterRegistrationBean<PhysicalTenantStatusScopeFilter> physicalTenantStatusScopeFilter;

  @BeforeEach
  void setup() {
    when(serviceRegistry.topologyServices(any())).thenReturn(topologyServices);
    when(topologyServices.getStatus())
        .thenReturn(CompletableFuture.completedFuture(ClusterStatus.HEALTHY));
  }

  @Test
  void shouldRegisterStatusScopeFilterBeforeSpringSecuritysFilterChainProxy() {
    // the rejection must happen before Spring Security selects a per-tenant chain (ADR 001 D3);
    // a lower order runs earlier in the chain
    assertThat(physicalTenantStatusScopeFilter.getOrder())
        .as(
            "PhysicalTenantStatusScopeFilter must run before Spring Security's FilterChainProxy"
                + " (order %d) so per-tenant chain selection never sees a rejected request",
            SPRING_SECURITY_DEFAULT_FILTER_ORDER)
        .isLessThan(SPRING_SECURITY_DEFAULT_FILTER_ORDER);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/physical-tenants/tenanta/v2/status",
        "/physical-tenants/unknown/v2/status",
      })
  void shouldReturnUniform404ForNonDefaultTenantStatusBeforeReachingController(final String uri) {
    // when / then
    webClient
        .get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            "{\"type\":\"about:blank\",\"status\":404,\"title\":\"Not Found\"}",
            JsonCompareMode.STRICT);

    // the request must never reach the controller — or a per-tenant security chain — at all
    verify(topologyServices, never()).getStatus();
  }

  @Test
  void shouldReturnIdenticalBodyAndContentTypeForExistingAndUnknownTenant() {
    // an existing non-default tenant and an unknown one must be indistinguishable: same status,
    // same body, same content type — guards against error-page dispatch embedding the request path
    final var existingTenant =
        webClient.get().uri("/physical-tenants/tenanta/v2/status").exchange();
    final var unknownTenant = webClient.get().uri("/physical-tenants/unknown/v2/status").exchange();

    final var existingBody = existingTenant.expectBody(String.class).returnResult();
    final var unknownBody = unknownTenant.expectBody(String.class).returnResult();

    assertThat(existingBody.getStatus()).isEqualTo(unknownBody.getStatus());
    assertThat(existingBody.getResponseBody()).isEqualTo(unknownBody.getResponseBody());
    assertThat(existingBody.getResponseHeaders().getContentType())
        .isEqualTo(unknownBody.getResponseHeaders().getContentType());
  }

  @Test
  void shouldReachControllerForDefaultTenantPrefixedPath() {
    // when / then
    webClient
        .get()
        .uri("/physical-tenants/default/v2/status")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(topologyServices).getStatus();
  }

  @Test
  void shouldReachControllerForUnprefixedPath() {
    // when / then
    webClient
        .get()
        .uri("/v2/status")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(topologyServices).getStatus();
  }
}
