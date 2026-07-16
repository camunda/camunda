/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.eventbridge.streaming.changelog.ChangelogKeyEnvelope;
import io.camunda.eventbridge.streaming.changelog.ChangelogKeyEnvelope.Envelope;
import io.camunda.eventbridge.streaming.changelog.ChangelogRecord;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The base projection's changelog records (streaming ADR 0009 Decisions 1/2): {@link
 * StateBackedProjectionState#changelogRecords()} mirrors exactly what {@link
 * StateBackedProjectionState#persistFrozen()} writes for every column family it owns, enveloped
 * with each family's own {@link AnalyticsColumnFamilies} tag so the several families can share one
 * changelog topic.
 */
final class StateBackedProjectionStateChangelogTest {

  @Test
  void shouldEnvelopeAnElementEntityPutUnderItsColumnFamilyTag() throws Exception {
    // given
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(1L, 1_000L, true, -1L, null);

      // when
      state.freeze();
      final List<ChangelogRecord> records = state.changelogRecords();

      // then — exactly one put, enveloped under the ELEMENT_ENTITY tag
      final List<ChangelogRecord> puts = records.stream().filter(r -> !r.isTombstone()).toList();
      assertThat(puts).hasSize(1);
      final Envelope decoded = ChangelogKeyEnvelope.decode(puts.get(0).key());
      assertThat(decoded.cfTag()).isEqualTo(AnalyticsColumnFamilies.ELEMENT_ENTITY.getValue());

      // and persisting afterwards writes the identical row this record already carries
      state.persistFrozen();
      state.completeFrozen(true);
      assertThat(state.element(1L)).isNotNull();
    }
  }

  @Test
  void shouldProduceNoChangelogRecordForAVariableCreatedAndDeletedWithinOneCut() throws Exception {
    // given — a scope that sets and then clears a variable before ever being checkpointed (the
    // flushed-flag absorption guarantee, observed end-to-end through the base projection)
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(100L, buf("a"), buf("1"));
      state.clearVariables(100L);

      // when
      state.freeze();
      final List<ChangelogRecord> records = state.changelogRecords();

      // then — the variable and its scope marker were both born and died before the freeze, so
      // neither ever reached the frozen snapshot; the changelog carries nothing for them
      assertThat(records).isEmpty();
      state.persistFrozen();
      state.completeFrozen(true);
    }
  }

  @Test
  void shouldTombstoneAFlushedThenDeletedVariable() throws Exception {
    // given — a variable checkpointed once (flushed), then deleted in the next cut
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(200L, buf("x"), buf("1"));
      state.checkpoint();
      state.clearVariables(200L);

      // when
      state.freeze();
      final List<ChangelogRecord> records = state.changelogRecords();

      // then — a real tombstone for the flushed variable (and its now-cleared scope marker),
      // enveloped under their respective tags
      final List<ChangelogRecord> tombstones =
          records.stream().filter(ChangelogRecord::isTombstone).toList();
      assertThat(tombstones).isNotEmpty();
      final List<Integer> tags =
          tombstones.stream()
              .map(r -> ChangelogKeyEnvelope.decode(r.key()).cfTag())
              .distinct()
              .toList();
      assertThat(tags)
          .containsAnyOf(
              AnalyticsColumnFamilies.VARIABLE_ENTRIES.getValue(),
              AnalyticsColumnFamilies.VARIABLE_SCOPES.getValue());
      state.persistFrozen();
      state.completeFrozen(true);
    }
  }

  @Test
  void shouldNotReemitAnUnchangedRowAcrossCuts() throws Exception {
    // given — one element checkpointed, then a second cut that touches an unrelated instance
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(1L, 1_000L, true, -1L, null);
      state.checkpoint();

      state.activateElement(2L, 2_000L, true, -1L, null);
      state.freeze();
      final List<ChangelogRecord> records = state.changelogRecords();

      // then — only the newly-activated element is in the changelog
      assertThat(records).hasSize(1);
      state.persistFrozen();
      state.completeFrozen(true);
    }
  }

  @Test
  void shouldReturnEmptyForAnEmptyFrozenCut() throws Exception {
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.freeze();
      assertThat(state.changelogRecords()).isEmpty();
      state.persistFrozen();
      state.completeFrozen(true);
    }
  }

  private static DirectBuffer buf(final String value) {
    return new UnsafeBuffer(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
