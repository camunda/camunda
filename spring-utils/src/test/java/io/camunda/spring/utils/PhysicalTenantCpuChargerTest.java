/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class PhysicalTenantCpuChargerTest {

  private static final long BURN_NANOS = Duration.ofMillis(50).toNanos();

  private final List<String> chargedTenants = new ArrayList<>();
  private final List<Long> chargedNanos = new ArrayList<>();
  private final PhysicalTenantCpuCharger charger =
      new PhysicalTenantCpuCharger(
          (tenant, nanos) -> {
            chargedTenants.add(tenant);
            chargedNanos.add(nanos);
          });

  @Test
  void shouldChargeCpuTimeOfScopeToTenant() {
    // given
    final var scope = charger.open();

    // when
    burn();
    scope.chargeTo("blue");

    // then
    assertThat(chargedTenants).containsExactly("blue");
    assertThat(chargedNanos)
        .singleElement()
        .satisfies(n -> assertThat(n).isGreaterThan(BURN_NANOS));
  }

  @Test
  void shouldChargeScopeInsideAnotherScopeOnlyOnce() {
    // given
    final var outer = charger.open();

    // when
    final var inner = charger.open();
    burn();
    inner.chargeTo("blue");
    outer.chargeTo("blue");

    // then
    assertThat(chargedTenants).containsExactly("blue");
  }

  @Test
  void shouldChargeNothingWithoutSink() {
    // when
    final var scope = PhysicalTenantCpuCharger.none().open();
    burn();
    scope.chargeTo("blue");

    // then
    assertThat(chargedTenants).isEmpty();
  }

  @Test
  void shouldChargeTasksOfExecutorToTheirTenant() throws Exception {
    // given
    final var delegate = Executors.newSingleThreadExecutor();
    final var executor = new PhysicalTenantPropagatingExecutorService(delegate, charger);
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "blue");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    try {
      // when
      executor.submit(PhysicalTenantCpuChargerTest::burn).get();

      // then
      assertThat(chargedTenants).containsExactly("blue");
      assertThat(chargedNanos)
          .singleElement()
          .satisfies(n -> assertThat(n).isGreaterThan(BURN_NANOS));
    } finally {
      RequestContextHolder.resetRequestAttributes();
      delegate.shutdownNow();
    }
  }

  private static void burn() {
    final var bean = ManagementFactory.getThreadMXBean();
    final long start = bean.getCurrentThreadCpuTime();
    while (bean.getCurrentThreadCpuTime() - start < BURN_NANOS + 1_000_000) {
      Thread.onSpinWait();
    }
  }
}
