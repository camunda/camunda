/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.state.TranslatorState.VariantAccumulator;
import io.camunda.analytics.lake.state.TranslatorState.VariantElementKind;
import io.camunda.analytics.lake.state.TranslatorState.VariantName;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for the variant-k1 accumulator/name-map wire encoding {@link
 * RocksDbTranslatorState} adds — {@link VariantAccumulatorValue} and {@link VariantNameValue} —
 * over a real RocksDB store (not an in-memory fake), including across a close/reopen to prove the
 * encoding is actually durable, not just correct in an in-process object graph.
 */
class RocksDbTranslatorStateVariantTest {

  @TempDir Path stateDir;

  @Test
  void shouldRoundTripAVariantAccumulatorWithMultipleSeenHashes() {
    // given: a nontrivial seen-set, including a negative int (h32's sign bit may be set -- it is
    // stored/read as a raw 32-bit value, not an unsigned magnitude)
    final int[] seenHashes = {-100, 5, 42, 1_000_000};
    final VariantAccumulator accumulator = new VariantAccumulator(77L, -123456789L, 4, seenHashes);

    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.putVariantAccumulator(1L, accumulator);
      final VariantAccumulator roundTripped = state.getVariantAccumulator(1L);

      // then
      assertThat(roundTripped.lastPosition()).isEqualTo(77L);
      assertThat(roundTripped.hash()).isEqualTo(-123456789L);
      assertThat(roundTripped.count()).isEqualTo(4);
      assertThat(roundTripped.seenHashes()).containsExactly(-100, 5, 42, 1_000_000);
    }
  }

  @Test
  void shouldRoundTripAnAccumulatorWithNoSeenHashesYet() {
    // given: a freshly-opened instance's accumulator (see LakeTranslator's own javadoc)
    final VariantAccumulator fresh = new VariantAccumulator(-1L, 0L, 0, new int[0]);

    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.putVariantAccumulator(5L, fresh);
      final VariantAccumulator roundTripped = state.getVariantAccumulator(5L);

      // then
      assertThat(roundTripped.lastPosition()).isEqualTo(-1L);
      assertThat(roundTripped.hash()).isZero();
      assertThat(roundTripped.count()).isZero();
      assertThat(roundTripped.seenHashes()).isEmpty();
    }
  }

  @Test
  void shouldReturnNullForAnUnknownInstance() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      assertThat(state.getVariantAccumulator(999L)).isNull();
    }
  }

  @Test
  void shouldDeleteAVariantAccumulator() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given
      state.putVariantAccumulator(1L, new VariantAccumulator(0L, 1L, 1, new int[] {7}));

      // when
      state.deleteVariantAccumulator(1L);

      // then
      assertThat(state.getVariantAccumulator(1L)).isNull();
    }
  }

  @Test
  void shouldSurviveACloseAndReopen() {
    // given
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      state.putVariantAccumulator(1L, new VariantAccumulator(42L, 99L, 2, new int[] {3, 9}));
    }

    // when: a fresh RocksDbTranslatorState over the same directory, as at process restart
    try (RocksDbTranslatorState reopened = new RocksDbTranslatorState(stateDir)) {
      final VariantAccumulator roundTripped = reopened.getVariantAccumulator(1L);

      // then
      assertThat(roundTripped.lastPosition()).isEqualTo(42L);
      assertThat(roundTripped.hash()).isEqualTo(99L);
      assertThat(roundTripped.seenHashes()).containsExactly(3, 9);
    }
  }

  @Test
  void shouldRoundTripVariantNamesForBothKinds() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // given/when
      state.putVariantName("proc-a", 111, new VariantName("task-a", VariantElementKind.ELEMENT));
      state.putVariantName("proc-a", 222, new VariantName("flow-a", VariantElementKind.FLOW));

      // then
      final VariantName element = state.getVariantName("proc-a", 111);
      assertThat(element.id()).isEqualTo("task-a");
      assertThat(element.kind()).isEqualTo(VariantElementKind.ELEMENT);

      final VariantName flow = state.getVariantName("proc-a", 222);
      assertThat(flow.id()).isEqualTo("flow-a");
      assertThat(flow.kind()).isEqualTo(VariantElementKind.FLOW);
    }
  }

  @Test
  void shouldReturnNullForAnUnknownVariantName() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      assertThat(state.getVariantName("proc-a", 1)).isNull();
    }
  }

  @Test
  void shouldKeepDifferentProcessesNameMapsIndependentEvenWhenTheirH32sCoincide() {
    // given: two different processes, each recording a name under the SAME h32 value -- the key
    // must be scoped per process id, not just per h32
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.putVariantName("proc-a", 42, new VariantName("task-in-a", VariantElementKind.ELEMENT));
      state.putVariantName("proc-b", 42, new VariantName("task-in-b", VariantElementKind.ELEMENT));

      // then: both are retrievable independently, neither overwrote the other
      assertThat(state.getVariantName("proc-a", 42).id()).isEqualTo("task-in-a");
      assertThat(state.getVariantName("proc-b", 42).id()).isEqualTo("task-in-b");
    }
  }

  @Test
  void shouldKeepProcessIdsOfDifferentLengthsSharingACommonPrefixIndependent() {
    // given: "proc" and "proc-longer" share a byte prefix -- proves the fixed-width h32 suffix
    // (see RocksDbTranslatorState#variantNameKey's own javadoc) keeps the two keyspaces disjoint
    // without needing a length prefix on the variable-length process id
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.putVariantName("proc", 7, new VariantName("short", VariantElementKind.ELEMENT));
      state.putVariantName("proc-longer", 7, new VariantName("long", VariantElementKind.FLOW));

      // then
      assertThat(state.getVariantName("proc", 7).id()).isEqualTo("short");
      assertThat(state.getVariantName("proc-longer", 7).id()).isEqualTo("long");
    }
  }
}
