/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class PhysicalTenantServedFilterTest {

  private final DefaultPhysicalTenantRegistry registry =
      new DefaultPhysicalTenantRegistry(List.of(tenant("tenanta")));
  private final Set<String> withChains = ConcurrentHashMap.newKeySet();
  private final PhysicalTenantServedFilter filter =
      new PhysicalTenantServedFilter(registry, withChains::contains);

  @Test
  void shouldPassATenantWhoseChainsServe() throws Exception {
    // given
    withChains.add("tenanta");

    // when
    final var response = request("/physical-tenants/tenanta/v2/topology");

    // then
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.passed()).isTrue();
  }

  @Test
  void shouldRejectATenantWhoseChainsDoNotServeYet() throws Exception {
    // given
    registry.add(tenant("added"));

    // when
    final var response = request("/physical-tenants/added/v2/topology");

    // then
    assertThat(response.status()).isEqualTo(404);
    assertThat(response.passed()).isFalse();
  }

  @Test
  void shouldRejectARemovedTenantWhileItsChainsStillServe() throws Exception {
    // given
    withChains.add("tenanta");
    registry.remove("tenanta");

    // when
    final var response = request("/physical-tenants/tenanta/v2/topology");

    // then
    assertThat(response.status()).isEqualTo(404);
  }

  @Test
  void shouldRejectAnUnknownTenant() throws Exception {
    // given
    withChains.add("unknown");

    // when
    final var response = request("/physical-tenants/unknown/v2/topology");

    // then
    assertThat(response.status()).isEqualTo(404);
  }

  @Test
  void shouldPassEveryTenantOfTheNodeWithoutScopedChains() throws Exception {
    // given
    registry.add(tenant("added"));
    final var withoutScopedChains = new PhysicalTenantServedFilter(registry);

    // when
    final var response = request(withoutScopedChains, "/physical-tenants/added/v2/topology");

    // then
    assertThat(response.passed()).isTrue();
  }

  private Result request(final String path) throws Exception {
    return request(filter, path);
  }

  private static Result request(final PhysicalTenantServedFilter filter, final String path)
      throws Exception {
    final var request = new MockHttpServletRequest("GET", path);
    final var response = new MockHttpServletResponse();
    final var chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response.getStatus(), chain.getRequest() != null);
  }

  private static PhysicalTenant tenant(final String id) {
    return new PhysicalTenant(id, null, UUID.randomUUID());
  }

  private record Result(int status, boolean passed) {}
}
