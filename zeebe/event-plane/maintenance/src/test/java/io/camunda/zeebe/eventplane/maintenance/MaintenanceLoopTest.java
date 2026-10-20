/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

final class MaintenanceLoopTest extends StoreTestCase {

  @Test
  void shouldRunMaintenanceOnceBeforeItWaitsForTheStop() throws Exception {
    // given
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    final var maintenance =
        new Maintenance(store, new MaintenanceConfig(Duration.ofDays(1), 1 << 20, null), clock);

    // when: the stop is counted down already
    MaintenanceApp.runEvery(maintenance, Duration.ofHours(1), new CountDownLatch(0));

    // then
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1");
  }

  @Test
  void shouldGoOnAfterAFailedRun() throws Exception {
    // given
    store.failListing();
    final var maintenance =
        new Maintenance(store, new MaintenanceConfig(Duration.ofDays(1), 1 << 20, null), clock);

    // when
    MaintenanceApp.runEvery(maintenance, Duration.ofHours(1), new CountDownLatch(0));

    // then: it returned, instead of throwing
    assertThat(store.writes()).isEmpty();
  }
}
