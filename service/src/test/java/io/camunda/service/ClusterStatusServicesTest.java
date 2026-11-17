/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.ClusterStatusServices.AggregatedStatus.DEGRADED;
import static io.camunda.service.ClusterStatusServices.AggregatedStatus.DOWN;
import static io.camunda.service.ClusterStatusServices.AggregatedStatus.HEALTHY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterStatusServices.AggregatedStatus;
import io.camunda.service.TopologyServices.ClusterStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClusterStatusServicesTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldBeHealthyWhenTheOnlyTenantHasAHealthyLeaderAndReadyStorage() {
    // given
    final var services = services(Map.of(TENANT_A, ClusterStatus.HEALTHY), Set.of(TENANT_A));

    // when / then
    assertThat(statusOf(services)).isEqualTo(HEALTHY);
  }

  @Test
  void shouldBeDegradedWhenTheOnlyTenantHasDegradedSecondaryStorage() {
    // given — a healthy leader, so the tenant can still process work
    final var services = services(Map.of(TENANT_A, ClusterStatus.HEALTHY), Set.of());

    // when / then
    assertThat(statusOf(services)).isEqualTo(DEGRADED);
  }

  @Test
  void shouldBeDownWhenTheOnlyTenantHasNoHealthyLeader() {
    // given — ready storage must not mask an unusable partition group
    final var services = services(Map.of(TENANT_A, ClusterStatus.UNHEALTHY), Set.of(TENANT_A));

    // when / then
    assertThat(statusOf(services)).isEqualTo(DOWN);
  }

  @Test
  void shouldBeHealthyWhenAllTenantsAreHealthy() {
    // given
    final var services =
        services(
            Map.of(TENANT_A, ClusterStatus.HEALTHY, TENANT_B, ClusterStatus.HEALTHY),
            Set.of(TENANT_A, TENANT_B));

    // when / then
    assertThat(statusOf(services)).isEqualTo(HEALTHY);
  }

  @Test
  void shouldBeDegradedWhenOneTenantHasDegradedSecondaryStorage() {
    // given
    final var services =
        services(
            Map.of(TENANT_A, ClusterStatus.HEALTHY, TENANT_B, ClusterStatus.HEALTHY),
            Set.of(TENANT_A));

    // when / then
    assertThat(statusOf(services)).isEqualTo(DEGRADED);
  }

  @Test
  void shouldBeDegradedWhenOneTenantIsDownAndAnotherIsHealthy() {
    // given
    final var services =
        services(
            Map.of(TENANT_A, ClusterStatus.HEALTHY, TENANT_B, ClusterStatus.UNHEALTHY),
            Set.of(TENANT_A, TENANT_B));

    // when / then
    assertThat(statusOf(services)).isEqualTo(DEGRADED);
  }

  @Test
  void shouldBeDownOnlyWhenNoTenantCanProcessWork() {
    // given
    final var services =
        services(
            Map.of(TENANT_A, ClusterStatus.UNHEALTHY, TENANT_B, ClusterStatus.UNHEALTHY),
            Set.of(TENANT_A, TENANT_B));

    // when / then
    assertThat(statusOf(services)).isEqualTo(DOWN);
  }

  @Test
  void shouldBeDownWithoutAnyKnownTenant() {
    // given
    final var services = new ClusterStatusServices(Map.of(), tenantId -> true);

    // when / then
    assertThat(statusOf(services)).isEqualTo(DOWN);
  }

  @Test
  void shouldCountATenantWhoseStatusCannotBeDeterminedAsUnhealthy() {
    // given — one tenant is healthy, the other's status cannot be determined
    final var services =
        new ClusterStatusServices(
            Map.of(TENANT_A, healthy(), TENANT_B, indeterminate()), tenantId -> true);

    // when / then — the indeterminate tenant counts as DOWN rather than failing the whole request,
    // so the aggregate is DEGRADED and the response stays within the declared status codes
    assertThat(statusOf(services)).isEqualTo(DEGRADED);
  }

  @Test
  void shouldBeDownWhenNoTenantStatusCanBeDetermined() {
    // given
    final var services =
        new ClusterStatusServices(Map.of(TENANT_A, indeterminate()), tenantId -> true);

    // when / then — never HEALTHY, and never a failed future: propagating would answer 500 with an
    // exception message in the body, on an endpoint that must reveal nothing to anonymous callers
    assertThat(statusOf(services)).isEqualTo(DOWN);
  }

  private static TopologyServices healthy() {
    final var topologyServices = mock(TopologyServices.class);
    when(topologyServices.getStatus())
        .thenReturn(CompletableFuture.completedFuture(ClusterStatus.HEALTHY));
    return topologyServices;
  }

  private static TopologyServices indeterminate() {
    final var topologyServices = mock(TopologyServices.class);
    when(topologyServices.getStatus())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("topology unavailable")));
    return topologyServices;
  }

  private static ClusterStatusServices services(
      final Map<String, ClusterStatus> topologyStatusByTenant,
      final Set<String> tenantsWithReadyStorage) {
    final Map<String, TopologyServices> topologyServices = new LinkedHashMap<>();
    topologyStatusByTenant.forEach(
        (tenantId, status) -> {
          final var mocked = mock(TopologyServices.class);
          when(mocked.getStatus()).thenReturn(CompletableFuture.completedFuture(status));
          topologyServices.put(tenantId, mocked);
        });
    return new ClusterStatusServices(topologyServices, tenantsWithReadyStorage::contains);
  }

  private static AggregatedStatus statusOf(final ClusterStatusServices services) {
    return services.getStatus().join();
  }
}
