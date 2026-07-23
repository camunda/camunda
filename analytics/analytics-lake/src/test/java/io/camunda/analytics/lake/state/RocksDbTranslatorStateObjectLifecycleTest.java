/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.state.TranslatorState.BirthQualifier;
import io.camunda.analytics.lake.state.TranslatorState.LifecycleStatus;
import io.camunda.analytics.lake.state.TranslatorState.ObjectLifecycle;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for the object-lifecycle wire encoding {@link RocksDbTranslatorState} adds —
 * {@link ObjectLifecycleValue} and the {@code objectLifecycleKey} length-prefixed-first-component
 * encoding — over a real RocksDB store, plus the tombstone-retention sweep ({@link
 * TranslatorState#sweepObjectLifecycleTombstones}).
 */
class RocksDbTranslatorStateObjectLifecycleTest {

  @TempDir Path stateDir;

  @Test
  void shouldRoundTripAnOpenLifecycle() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given
      final ObjectLifecycle open =
          new ObjectLifecycle(LifecycleStatus.OPEN, 1_000L, BirthQualifier.FIRST_SIGHTING, 3, 0L);

      // when
      state.putObjectLifecycle("customer", "cust-1", open);
      final ObjectLifecycle roundTripped = state.getObjectLifecycle("customer", "cust-1");

      // then
      assertThat(roundTripped.status()).isEqualTo(LifecycleStatus.OPEN);
      assertThat(roundTripped.birthTsMs()).isEqualTo(1_000L);
      assertThat(roundTripped.birthQualifier()).isEqualTo(BirthQualifier.FIRST_SIGHTING);
      assertThat(roundTripped.nSightings()).isEqualTo(3);
      assertThat(roundTripped.closedAtMs()).isZero();
    }
  }

  @Test
  void shouldRoundTripAClosedTombstone() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given
      final ObjectLifecycle closed =
          new ObjectLifecycle(
              LifecycleStatus.CLOSED_TOMBSTONE, 1_000L, BirthQualifier.FIRST_SIGHTING, 5, 9_000L);

      // when
      state.putObjectLifecycle("dispute", "disp-1", closed);
      final ObjectLifecycle roundTripped = state.getObjectLifecycle("dispute", "disp-1");

      // then
      assertThat(roundTripped.status()).isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
      assertThat(roundTripped.closedAtMs()).isEqualTo(9_000L);
      assertThat(roundTripped.nSightings()).isEqualTo(5);
    }
  }

  @Test
  void shouldReturnNullForAnUnknownObject() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      assertThat(state.getObjectLifecycle("customer", "unknown")).isNull();
    }
  }

  @Test
  void shouldSurviveACloseAndReopen() {
    // given
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      state.putObjectLifecycle(
          "customer",
          "cust-1",
          new ObjectLifecycle(LifecycleStatus.OPEN, 42L, BirthQualifier.FIRST_SIGHTING, 1, 0L));
    }

    // when: a fresh RocksDbTranslatorState over the same directory, as at process restart
    try (RocksDbTranslatorState reopened = new RocksDbTranslatorState(stateDir)) {
      final ObjectLifecycle roundTripped = reopened.getObjectLifecycle("customer", "cust-1");

      // then
      assertThat(roundTripped.birthTsMs()).isEqualTo(42L);
      assertThat(roundTripped.status()).isEqualTo(LifecycleStatus.OPEN);
    }
  }

  @Test
  void shouldKeepDifferentTypeIdSplitsOfTheSameConcatenationIndependent() {
    // given: "ab"+"cd" and "a"+"bcd" concatenate to the identical raw string "abcd" -- proves the
    // length prefix on the FIRST component alone (see RocksDbTranslatorState#objectLifecycleKey's
    // own javadoc) is what keeps these two distinct (type, id) pairs from colliding, since the
    // explicit length says exactly where the type ends regardless of what the remaining bytes
    // happen to spell.
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.putObjectLifecycle(
          "ab",
          "cd",
          new ObjectLifecycle(LifecycleStatus.OPEN, 1L, BirthQualifier.FIRST_SIGHTING, 1, 0L));
      state.putObjectLifecycle(
          "a",
          "bcd",
          new ObjectLifecycle(LifecycleStatus.OPEN, 2L, BirthQualifier.FIRST_SIGHTING, 2, 0L));

      // then: both are retrievable independently, neither overwrote the other
      assertThat(state.getObjectLifecycle("ab", "cd").birthTsMs()).isEqualTo(1L);
      assertThat(state.getObjectLifecycle("ab", "cd").nSightings()).isEqualTo(1);
      assertThat(state.getObjectLifecycle("a", "bcd").birthTsMs()).isEqualTo(2L);
      assertThat(state.getObjectLifecycle("a", "bcd").nSightings()).isEqualTo(2);
    }
  }

  @Test
  void shouldKeepDifferentObjectTypesWithTheSameIdIndependent() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when: same object id, different declared types
      state.putObjectLifecycle(
          "customer",
          "1",
          new ObjectLifecycle(LifecycleStatus.OPEN, 10L, BirthQualifier.FIRST_SIGHTING, 1, 0L));
      state.putObjectLifecycle(
          "dispute",
          "1",
          new ObjectLifecycle(LifecycleStatus.OPEN, 20L, BirthQualifier.FIRST_SIGHTING, 1, 0L));

      // then
      assertThat(state.getObjectLifecycle("customer", "1").birthTsMs()).isEqualTo(10L);
      assertThat(state.getObjectLifecycle("dispute", "1").birthTsMs()).isEqualTo(20L);
    }
  }

  @Test
  void shouldSweepOnlyClosedTombstonesOlderThanTheCutoff() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given: an open object, a recently-closed tombstone, and an old tombstone
      state.putObjectLifecycle(
          "customer",
          "open-1",
          new ObjectLifecycle(LifecycleStatus.OPEN, 1L, BirthQualifier.FIRST_SIGHTING, 1, 0L));
      state.putObjectLifecycle(
          "customer",
          "recent-1",
          new ObjectLifecycle(
              LifecycleStatus.CLOSED_TOMBSTONE, 1L, BirthQualifier.FIRST_SIGHTING, 1, 9_000L));
      state.putObjectLifecycle(
          "customer",
          "old-1",
          new ObjectLifecycle(
              LifecycleStatus.CLOSED_TOMBSTONE, 1L, BirthQualifier.FIRST_SIGHTING, 1, 1_000L));

      // when: cutoff is between the two tombstones' own closedAtMs
      final int swept = state.sweepObjectLifecycleTombstones(5_000L);

      // then: only the older tombstone is removed
      assertThat(swept).isEqualTo(1);
      assertThat(state.getObjectLifecycle("customer", "old-1")).isNull();
      assertThat(state.getObjectLifecycle("customer", "recent-1")).isNotNull();
      assertThat(state.getObjectLifecycle("customer", "open-1")).isNotNull();
    }
  }

  @Test
  void shouldNeverSweepAnOpenEntryRegardlessOfAge() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given: an OPEN accumulator with an ancient birth timestamp -- closedAtMs is meaningless
      // (0) for an OPEN entry, so even a very high cutoff must never sweep it
      state.putObjectLifecycle(
          "customer",
          "ancient",
          new ObjectLifecycle(LifecycleStatus.OPEN, 1L, BirthQualifier.FIRST_SIGHTING, 1, 0L));

      // when
      final int swept = state.sweepObjectLifecycleTombstones(Long.MAX_VALUE);

      // then
      assertThat(swept).isZero();
      assertThat(state.getObjectLifecycle("customer", "ancient")).isNotNull();
    }
  }

  @Test
  void shouldReturnZeroWhenNothingQualifiesForSweep() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      state.putObjectLifecycle(
          "customer",
          "cust-1",
          new ObjectLifecycle(
              LifecycleStatus.CLOSED_TOMBSTONE, 1L, BirthQualifier.FIRST_SIGHTING, 1, 9_000L));

      // when: cutoff is before the tombstone's own closedAtMs
      final int swept = state.sweepObjectLifecycleTombstones(1_000L);

      // then
      assertThat(swept).isZero();
      assertThat(state.getObjectLifecycle("customer", "cust-1")).isNotNull();
    }
  }
}
