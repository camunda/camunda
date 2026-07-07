/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class MeterRegistryTest {

  @Test
  void shouldAllocateDistinctIdsPerMeter() {
    // given
    final MeterIdRegistry registry = new MeterIdRegistry(new InMemoryMeterIdStore());

    // when
    final int count = registry.aggIdFor(7L, "count");
    final int p95 = registry.aggIdFor(7L, "p95");

    // then
    assertThat(count).isNotEqualTo(p95);
  }

  @Test
  void shouldReturnSameIdForSameMeter() {
    // given
    final MeterIdRegistry registry = new MeterIdRegistry(new InMemoryMeterIdStore());

    // then repeated lookups are stable within a run
    assertThat(registry.aggIdFor(7L, "count")).isEqualTo(registry.aggIdFor(7L, "count"));
  }

  @Test
  void shouldKeepIdsStableAcrossRestart() {
    // given a first registry allocates ids and persists them to a shared store
    final InMemoryMeterIdStore store = new InMemoryMeterIdStore();
    final MeterIdRegistry before = new MeterIdRegistry(store);
    final int count = before.aggIdFor(7L, "count");
    final int p95 = before.aggIdFor(7L, "p95");

    // when a new registry reloads from the same store (a restart)
    final MeterIdRegistry after = new MeterIdRegistry(store);

    // then the same meters resolve to the same ids
    assertThat(after.aggIdFor(7L, "count")).isEqualTo(count);
    assertThat(after.aggIdFor(7L, "p95")).isEqualTo(p95);
  }

  @Test
  void shouldNotCollideAcrossCubes() {
    // given the same meter name in two different cubes
    final MeterIdRegistry registry = new MeterIdRegistry(new InMemoryMeterIdStore());

    // then they get distinct ids
    assertThat(registry.aggIdFor(7L, "count")).isNotEqualTo(registry.aggIdFor(9L, "count"));
  }

  @Test
  void shouldNotReuseIdsAfterRestartForNewMeters() {
    // given ids allocated then reloaded
    final InMemoryMeterIdStore store = new InMemoryMeterIdStore();
    final MeterIdRegistry before = new MeterIdRegistry(store);
    final int a = before.aggIdFor(1L, "a");
    final int b = before.aggIdFor(1L, "b");

    // when a restarted registry allocates a brand-new meter
    final MeterIdRegistry after = new MeterIdRegistry(store);
    final int c = after.aggIdFor(1L, "c");

    // then the new id does not collide with the reloaded ones (monotonic, no reuse)
    assertThat(c).isNotIn(a, b);
  }

  @Test
  void shouldLookUpExistingWithoutAllocating() {
    // given
    final MeterIdRegistry registry = new MeterIdRegistry(new InMemoryMeterIdStore());
    final MeterKey key = new MeterKey(1L, "count");

    // then a lookup before allocation is empty and does not allocate
    assertThat(registry.existing(key)).isEmpty();
    final int id = registry.aggIdFor(key);
    assertThat(registry.existing(key)).hasValue(id);
  }
}
