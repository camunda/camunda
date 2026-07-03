/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.fact.ProcessDefinitionFact;
import io.camunda.analytics.fact.ProcessExecutionFact;
import io.camunda.analytics.metric.JdbcProcessDefinitionSink;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.ProcessExecutionProjector;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.ProjectionStage;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.Aggregation;
import io.camunda.eventbridge.streaming.aggregate.TypeRoutingAggregation;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One source partition's self-contained analytics shard: it owns a RocksDB provider (its own
 * directory), the base projection over it, the per-metric combiners, and a partial publisher — so
 * it is a {@link Task} that {@link #ownsDurability() owns its durability}. The runtime materializes
 * one per assigned partition and drives it single-threaded; {@link #restore()} resumes from the
 * shard's own committed offset and {@link #commit(long)} makes one atomic per-partition cut
 * (partials published, then state + offset persisted in the shard's own transaction).
 *
 * <p>State is physically isolated per partition (a directory each), so shards never share keys and
 * a partition is a natural snapshot unit for rebalance handoff.
 */
public final class ProjectionShard implements Task<SourceRecord>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ProjectionShard.class);

  private final int partition;
  private final StateBackedProjectionStore store;
  private final StreamProcessor<SourceRecord> processor;
  private final TransactionRunner transactionRunner;
  private final Runnable produceFlush;
  private final AutoCloseable resource;

  ProjectionShard(
      final int partition,
      final StateBackedProjectionStore store,
      final StreamProcessor<SourceRecord> processor,
      final TransactionRunner transactionRunner,
      final Runnable produceFlush,
      final AutoCloseable resource) {
    this.partition = partition;
    this.store = store;
    this.processor = processor;
    this.transactionRunner = transactionRunner;
    this.produceFlush = produceFlush;
    this.resource = resource;
  }

  /**
   * Opens a RocksDB-backed shard for {@code partition} under {@code baseDir}-p{partition}, wiring
   * the base projection, the combiners for every metric spec, and a per-shard partial publisher.
   */
  public static ProjectionShard open(
      final int partition,
      final EventBridgeClient client,
      final String baseDir,
      final String factsTopic,
      final int factsPartitions,
      final long slaMs,
      final JdbcProcessDefinitionSink definitionSink,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final StateBackedProjectionStore store = StateBackedProjectionStore.fromProvider(provider);
    final ProcessExecutionProjector projector =
        new ProcessExecutionProjector(store, store.elementStarts());
    final EventBridgePartialPublisher publisher =
        new EventBridgePartialPublisher(client, factsTopic, factsPartitions);

    final List<Aggregation<ProcessExecutionFact>> rollups = new ArrayList<>();
    for (final MetricSpec<?, ?, ?> spec : Metrics.specs(slaMs)) {
      rollups.add(
          StageBuilders.combiner(
              spec,
              provider.keyValueStore(
                  AnalyticsColumnFamilies.ROLLUP_CELLS, new DbBytes(), new DbBytes()),
              provider.keyValueStore(
                  AnalyticsColumnFamilies.ROLLUP_OFFSETS, new DbBytes(), new DbLong()),
              publisher,
              provider::runInTransaction));
    }
    // Process definitions are metadata (the BPMN XML), not a windowed aggregate — routed straight
    // to
    // the serving store; the upsert is idempotent, so seeing one on several shards is harmless.
    rollups.add(new TypeRoutingAggregation<>(ProcessDefinitionFact.class, definitionSink));

    final StreamProcessor<SourceRecord> processor =
        new StreamProcessor<SourceRecord>().add(new ProjectionStage<>(projector, rollups));
    return new ProjectionShard(
        partition, store, processor, provider::runInTransaction, publisher::flush, provider);
  }

  @Override
  public boolean ownsDurability() {
    return true;
  }

  @Override
  public void init() {
    processor.init();
  }

  @Override
  public long restore() {
    // The shard's own consumed position (NO_POSITION == NO_OFFSET == -1); the runtime dedups past
    // it.
    return store.getConsumedPosition(partition);
  }

  @Override
  public void process(final SourceRecord record) {
    processor.process(record);
  }

  @Override
  public void flush() {
    processor.flush();
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    processor.punctuateWallClock(wallClockMs);
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    processor.advanceStreamTime(streamTimeMs);
  }

  @Override
  public boolean needsCheckpoint() {
    return processor.needsCheckpoint();
  }

  @Override
  public void commit(final long offset) {
    // Produce-before-commit for this partition, then one atomic cut in the shard's own transaction:
    // partials durable, then this partition's state and offset persisted together.
    produceFlush.run();
    transactionRunner.runInTransaction(
        () -> {
          store.setConsumedPosition(partition, offset);
          processor.checkpoint();
        });
  }

  @Override
  public void close() {
    processor.close();
    try {
      resource.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for partition {}", partition, e);
    }
  }
}
