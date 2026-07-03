/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.fact.ProcessExecutionFact;
import io.camunda.analytics.shuffle.FactPublishSink;
import io.camunda.analytics.shuffle.MergingRollup;
import io.camunda.analytics.shuffle.PartialPublisher;
import io.camunda.analytics.shuffle.WriterKey;
import io.camunda.analytics.shuffle.WriterKeyCodec;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.DurableMaterializedRollup;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.TypeRoutingRollup;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import org.h2.jdbcx.JdbcDataSource;

/** Builds the Stage-1 combiner and the Stage-2 reducer for a {@link MetricSpec}. */
final class StageBuilders {

  private StageBuilders() {}

  /**
   * Stage 1: a windowed combiner (a {@code DurableMaterializedRollup} keyed by {@link WriterKey} so
   * each cell folds one source partition) that publishes its cells as partials to the facts topic.
   * Wrapped in a {@link TypeRoutingRollup} so it takes only its fact subtype from the projection.
   */
  static <F extends ProcessExecutionFact, K, ACC> Rollup<ProcessExecutionFact> combiner(
      final MetricSpec<F, K, ACC> spec,
      final KeyValueStore<DbBytes, DbBytes> cells,
      final KeyValueStore<DbBytes, DbLong> offsets,
      final PartialPublisher publisher,
      final TransactionRunner tx) {
    final DurableMaterializedRollup<F, WriterKey<K>, ACC> combiner =
        new DurableMaterializedRollup<>(
            spec.aggId(),
            spec.aggregate(),
            fact ->
                new WriterKey<>(spec.keySelector().getKey(fact), spec.coordinate().partition(fact)),
            spec.eventTime(),
            spec.coordinate(),
            TumblingWindows.ofSizeAndGrace(spec.windowMs(), spec.latenessMs()),
            new FactPublishSink<>(spec.aggId(), spec.keyCodec(), spec.accCodec(), publisher),
            cells,
            offsets,
            new WriterKeyCodec<>(spec.keyCodec()),
            spec.accCodec(),
            tx,
            spec.drained());
    return new TypeRoutingRollup<>(spec.factType(), combiner);
  }

  /**
   * Stage 2: a reducer that merges the metric's partials (per-writer slots) into the serving sink.
   */
  static <F extends ProcessExecutionFact, K, ACC> MergingRollup<K, ACC> merger(
      final MetricSpec<F, K, ACC> spec,
      final KeyValueStore<DbBytes, DbBytes> slots,
      final JdbcDataSource dataSource,
      final TransactionRunner tx) {
    return new MergingRollup<>(
        spec.aggId(),
        spec.aggregate(),
        TumblingWindows.ofSizeAndGrace(spec.windowMs(), spec.latenessMs()),
        spec.sinkFactory().apply(dataSource),
        slots,
        spec.keyCodec(),
        spec.accCodec(),
        tx,
        spec.drained());
  }
}
