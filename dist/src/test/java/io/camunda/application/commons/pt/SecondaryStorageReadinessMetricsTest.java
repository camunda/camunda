/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecondaryStorageReadinessMetricsTest {

  private static final String METRIC_NAME = "camunda.physical.tenant.secondary.storage.ready";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private SimpleMeterRegistry meterRegistry;
  private Set<String> readyTenants;
  private SecondaryStorageReadiness readiness;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    readyTenants = new HashSet<>(Set.of(TENANT_A, TENANT_B));
    readiness =
        new SecondaryStorageReadiness() {
          @Override
          public boolean isReady(final String physicalTenantId) {
            return readyTenants.contains(physicalTenantId);
          }

          @Override
          public boolean anyReady() {
            return !readyTenants.isEmpty();
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
        new SecondaryStorageReadinessMetrics(() -> Set.of(TENANT_A, TENANT_B), readiness);

    // when
    metrics.bindTo(meterRegistry);

    // then
    assertThat(gauge(TENANT_A)).isNotNull();
    assertThat(gauge(TENANT_B)).isNotNull();
  }

  @Test
  void shouldReportGaugeValueOneWhenTenantIsReady() {
    // given
    final var metrics =
        new SecondaryStorageReadinessMetrics(() -> Set.of(TENANT_A, TENANT_B), readiness);
    metrics.bindTo(meterRegistry);

    // when/then
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);
    assertThat(gauge(TENANT_B).value()).isEqualTo(1.0);
  }

  @Test
  void shouldFlipGaugeValueToZeroWhenTenantBecomesDegraded() {
    // given
    final var metrics =
        new SecondaryStorageReadinessMetrics(() -> Set.of(TENANT_A, TENANT_B), readiness);
    metrics.bindTo(meterRegistry);
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);

    // when - the gauge is pull-based, so mutating the underlying state must be reflected on the
    // next read without re-binding
    readyTenants.remove(TENANT_A);

    // then
    assertThat(gauge(TENANT_A).value()).isEqualTo(0.0);
    assertThat(gauge(TENANT_B).value()).isEqualTo(1.0);
  }

  @Test
  void shouldFlipGaugeValueBackToOneWhenTenantBecomesReadyAgain() {
    // given
    final var metrics =
        new SecondaryStorageReadinessMetrics(() -> Set.of(TENANT_A, TENANT_B), readiness);
    metrics.bindTo(meterRegistry);
    readyTenants.remove(TENANT_A);
    assertThat(gauge(TENANT_A).value()).isEqualTo(0.0);

    // when
    readyTenants.add(TENANT_A);

    // then
    assertThat(gauge(TENANT_A).value()).isEqualTo(1.0);
  }
}
