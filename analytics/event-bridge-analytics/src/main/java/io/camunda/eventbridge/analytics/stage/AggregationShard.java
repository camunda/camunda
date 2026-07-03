/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.shuffle.MergingRollup;
import io.camunda.analytics.shuffle.Partial;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One facts-topic partition's self-contained aggregation shard: it owns a RocksDB provider (its own
 * directory) holding the merge slots and the consumed facts offset, and the per-{@code aggId}
 * mergers that converge the idempotent serving sink. It is a {@link Task} that {@link
 * #ownsDurability() owns its durability}: {@link #restore()} resumes from the shard's own committed
 * facts offset and {@link #commit(long)} persists the slots and offset in one atomic per-partition
 * cut. The serving-sink upsert is idempotent and converged on {@code flush}, before the offset
 * advances.
 */
public final class AggregationShard implements Task<Partial>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AggregationShard.class);

  private final int partition;
  private final KeyValueStore<DbInt, DbLong> factsOffsets;
  private final StreamProcessor<Partial> processor;
  private final TransactionRunner transactionRunner;
  private final AutoCloseable resource;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  AggregationShard(
      final int partition,
      final KeyValueStore<DbInt, DbLong> factsOffsets,
      final StreamProcessor<Partial> processor,
      final TransactionRunner transactionRunner,
      final AutoCloseable resource) {
    this.partition = partition;
    this.factsOffsets = factsOffsets;
    this.processor = processor;
    this.transactionRunner = transactionRunner;
    this.resource = resource;
  }

  /**
   * Opens a RocksDB-backed shard for {@code partition} under {@code baseDir}-p{partition}, wiring
   * the merge slots and the mergers for every metric spec against the shared serving data source.
   */
  public static AggregationShard open(
      final int partition,
      final String baseDir,
      final long slaMs,
      final JdbcDataSource dataSource,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> slotStore =
        provider.keyValueStore(AnalyticsColumnFamilies.SLOT_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> factsOffsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());

    final Map<Integer, MergingRollup<?, ?>> mergers = new HashMap<>();
    for (final MetricSpec<?, ?, ?> spec : Metrics.specs(slaMs)) {
      mergers.put(
          spec.aggId(),
          StageBuilders.merger(spec, slotStore, dataSource, provider::runInTransaction));
    }

    final StreamProcessor<Partial> processor =
        new StreamProcessor<Partial>().add(new MergeStage(mergers));
    return new AggregationShard(
        partition, factsOffsets, processor, provider::runInTransaction, provider);
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
    offsetKey.wrapInt(partition);
    return factsOffsets.get(offsetKey).map(DbLong::getValue).orElse(NO_OFFSET);
  }

  @Override
  public void process(final Partial record) {
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
    // The serving-sink upsert is idempotent and already converged on flush(); here just persist the
    // slots and this partition's facts offset as one atomic cut in the shard's own transaction.
    transactionRunner.runInTransaction(
        () -> {
          offsetKey.wrapInt(partition);
          offsetValue.wrapLong(offset);
          factsOffsets.put(offsetKey, offsetValue);
          processor.checkpoint();
        });
  }

  @Override
  public void close() {
    processor.close();
    try {
      resource.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for facts partition {}", partition, e);
    }
  }
}
