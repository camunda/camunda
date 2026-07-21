/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.SecondaryStorageAvailability;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecondaryStorageAvailabilityMetricsTest {

  private static final String METRIC_NAME = "camunda.physical.tenant.secondary.storage.available";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private SimpleMeterRegistry meterRegistry;
  private Set<String> availableTenants;
  private SecondaryStorageAvailability availability;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    availableTenants = new HashSet<>(Set.of(TENANT_A, TENANT_B));
    availability =
        new SecondaryStorageAvailability() {
          @Override
          public boolean isAvailable(final String physicalTenantId) {
            return availableTenants.contains(physicalTenantId);
          }

          @Override
          public boolean anyAvailable() {
            return !availableTenants.isEmpty();
          }
        };
  }

  private Gauge gauge(final String physicalTenantId) {
    return meterRegistry.find(METRIC_NAME).tag("physicalTenant", physicalTenantId).gauge();
  }

  @Test
  void shouldRegisterGaugeForEachKnownPhysicalTenant() {
    // given
    final var metrics =
        new SecondaryStorageAvailabilityMetrics(() -> Set.of(TENANT_A, TENANT_B), availability);

    // when
    metrics.bindTo(meterRegistry);

    // then
    assertThat(gauge(TENANT_A)).isNotNull();
    assertThat(gauge(TENANT_B)).isNotNull();
  }

  @Test
  void shouldReportGaugeValueOneWhenTenantIsAvailable() {
    // given
    final var metrics =
        new SecondaryStorageAvailabilityMetrics(() -> Set.of(TENANT_A, TENANT_B), availability);
    metrics.bindTo(meterRegistry);

    // when/then
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);
    assertThat(gauge(TENANT_B).value()).isEqualTo(1.0);
  }

  @Test
  void shouldFlipGaugeValueToZeroWhenTenantBecomesDegraded() {
    // given
    final var metrics =
        new SecondaryStorageAvailabilityMetrics(() -> Set.of(TENANT_A, TENANT_B), availability);
    metrics.bindTo(meterRegistry);
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);

    // when - the gauge is pull-based, so mutating the underlying state must be reflected on the
    // next read without re-binding
    availableTenants.remove(TENANT_A);

    // then
    assertThat(gauge(TENANT_A).value()).isEqualTo(0.0);
    assertThat(gauge(TENANT_B).value()).isEqualTo(1.0);
  }

  @Test
  void shouldFlipGaugeValueBackToOneWhenTenantBecomesAvailableAgain() {
    // given
    final var metrics =
        new SecondaryStorageAvailabilityMetrics(() -> Set.of(TENANT_A, TENANT_B), availability);
    metrics.bindTo(meterRegistry);
    availableTenants.remove(TENANT_A);
    assertThat(gauge(TENANT_A).value()).isEqualTo(0.0);

    // when
    availableTenants.add(TENANT_A);

    // then
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);
  }
}
