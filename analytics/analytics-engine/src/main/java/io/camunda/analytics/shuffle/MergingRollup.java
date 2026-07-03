/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.MergingAggregation;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.function.Predicate;

/**
 * Stage-2 shuffle adapter: decodes the {@link Partial}s for one {@code aggId} and feeds them to the
 * generic {@link MergingAggregation} operator (per-writer slots → idempotent sink). Only the {@code
 * Partial} wire frame and the {@code aggId} identity are analytics-specific; all the merge
 * mechanics — per-writer slots, window finalization, durable slot storage — live in the generic
 * operator.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class MergingRollup<K, ACC> {

  private final int aggId;
  private final RecordValue<K> keyValue;
  private final RecordValue<ACC> accValue;
  private final MergingAggregation<K, ACC> merge;

  public MergingRollup(
      final int aggId,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx) {
    this(aggId, aggregate, windows, sink, slotStore, keyValue, accValue, tx, acc -> false);
  }

  public MergingRollup(
      final int aggId,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.aggId = aggId;
    this.keyValue = keyValue;
    this.accValue = accValue;
    // The aggId scopes this reducer's slots in the shared store (the operator's "group").
    merge =
        new MergingAggregation<>(
            aggId, aggregate, windows, sink, slotStore, keyValue, accValue, tx, drained);
  }

  /**
   * Decodes the partial (dropping those for another {@code aggId}) and merges its writer's slot.
   */
  public void accept(final Partial partial) {
    if (partial.aggId() != aggId) {
      return; // not ours (defensive; the driver dispatches by aggId)
    }
    final Windowed<K> cell =
        new Windowed<>(keyValue.fromBytes(partial.key()), partial.windowStart());
    merge.accept(partial.writer(), cell, accValue.fromBytes(partial.acc()));
  }

  public void flush() {
    merge.flush();
  }

  public void checkpoint() {
    merge.checkpoint();
  }

  public void close() {
    merge.close();
  }
}
