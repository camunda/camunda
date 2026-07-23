/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the process-definitions written-marker column family {@link RocksDbTranslatorState}
 * adds — see {@link LakeColumnFamilies#PROCESS_DEFINITIONS}'s own javadoc for why it exists — over
 * a real RocksDB store (not an in-memory fake), including across a close/reopen to prove the marker
 * is actually durable, not just correct in an in-process object graph.
 */
class RocksDbTranslatorStateProcessDefinitionTest {

  @TempDir Path stateDir;

  @Test
  void shouldReportUnknownForAProcessDefinitionNeverMarked() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      assertThat(state.hasProcessDefinition(1L)).isFalse();
    }
  }

  @Test
  void shouldReportKnownAfterMarking() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.markProcessDefinition(1L);

      // then
      assertThat(state.hasProcessDefinition(1L)).isTrue();
    }
  }

  @Test
  void shouldKeepDifferentProcessDefinitionKeysIndependent() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.markProcessDefinition(1L);

      // then: an unrelated key is unaffected
      assertThat(state.hasProcessDefinition(2L)).isFalse();
      assertThat(state.hasProcessDefinition(1L)).isTrue();
    }
  }

  @Test
  void shouldBeIdempotentToMarkTheSameKeyTwice() {
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.markProcessDefinition(1L);
      state.markProcessDefinition(1L);

      // then
      assertThat(state.hasProcessDefinition(1L)).isTrue();
    }
  }

  @Test
  void shouldSurviveACloseAndReopen() {
    // given
    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      state.markProcessDefinition(42L);
    }

    // when: a fresh RocksDbTranslatorState over the same directory, as at process restart
    try (RocksDbTranslatorState reopened = new RocksDbTranslatorState(stateDir)) {
      // then
      assertThat(reopened.hasProcessDefinition(42L)).isTrue();
      assertThat(reopened.hasProcessDefinition(43L)).isFalse();
    }
  }

  @Test
  void shouldHandleALargeProcessDefinitionKey() {
    // given: a Zeebe key near Long.MAX_VALUE -- proves the big-endian long encoding round-trips a
    // key whose sign bit would be set were it ever misread as a smaller signed type
    final long largeKey = Long.MAX_VALUE - 1;

    try (RocksDbTranslatorState state = new RocksDbTranslatorState(stateDir)) {
      // when
      state.markProcessDefinition(largeKey);

      // then
      assertThat(state.hasProcessDefinition(largeKey)).isTrue();
    }
  }
}
