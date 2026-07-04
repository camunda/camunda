/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.SegmentDedup;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One facts-topic partition's generic reduce shard (the declaration-driven replacement for {@code
 * AggregationShard}): it consumes {@link ShuffleEnvelope}s, dedups each batch by {@link
 * SegmentDedup} (per source partition), and dispatches its cell deltas by {@code aggId} to the
 * matching cube meter's {@link SegmentMergingAggregation}, which merges into one running cell and
 * converges the {@link CubeServingSink}. A {@link Task} that owns durability: {@link #restore()}
 * resumes from the committed facts offset and {@link #commit(long)} persists the merged cells + the
 * facts offset atomically.
 *
 * <p>Reference/upsert envelopes are idempotent by key and skip the dedup (TODO: handle reference
 * records, e.g. process-definition metadata, when the projected/reference path lands). TODO(e2e):
 * the segment dedup watermark is currently in-memory; the facts offset covers Stage-2 crashes, but
 * persist the watermark for producer re-emits across restarts during the e2e pass.
 */
public final class CubeAggregationShard implements Task<ShuffleEnvelope>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(CubeAggregationShard.class);

  /** Decodes and merges one cell delta into its meter's running cell. */
  private interface CellApplier {
    void apply(byte[] keyBytes, long windowStart, byte[] accBytes);
  }

  private final int partition;
  private final KeyValueStore<DbInt, DbLong> factsOffsets;
  private final SegmentDedup dedup;
  private final Map<Integer, CellApplier> byAggId;
  private final List<SegmentMergingAggregation<?, ?>> mergers;
  private final TransactionRunner transactionRunner;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final AutoCloseable resource;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  CubeAggregationShard(
      final int partition,
      final KeyValueStore<DbInt, DbLong> factsOffsets,
      final SegmentDedup dedup,
      final Map<Integer, CellApplier> byAggId,
      final List<SegmentMergingAggregation<?, ?>> mergers,
      final TransactionRunner transactionRunner,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final AutoCloseable resource) {
    this.partition = partition;
    this.factsOffsets = factsOffsets;
    this.dedup = dedup;
    this.byAggId = byAggId;
    this.mergers = mergers;
    this.transactionRunner = transactionRunner;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.resource = resource;
  }

  public static CubeAggregationShard open(
      final int partition,
      final String baseDir,
      final DatasetStore datasetStore,
      final List<ActiveCube> cubes,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        provider.keyValueStore(AnalyticsColumnFamilies.ROLLUP_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> factsOffsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DatasetWriter writer = datasetStore.writer();

    final Map<Integer, CellApplier> byAggId = new HashMap<>();
    final List<SegmentMergingAggregation<?, ?>> mergers = new ArrayList<>();
    for (final ActiveCube cube : cubes) {
      datasetStore.schemaManager().ensure(cube.compiled());
      for (final CompiledMeter meter : cube.compiled().meters()) {
        final SegmentMergingAggregation<DimensionKey, ?> merger =
            wire(cube, meter, writer, cellStore, provider::runInTransaction, byAggId);
        mergers.add(merger);
      }
    }
    return new CubeAggregationShard(
        partition,
        factsOffsets,
        new SegmentDedup(),
        byAggId,
        mergers,
        provider::runInTransaction,
        datasetStore,
        writer,
        provider);
  }

  /**
   * Wires one meter's merging aggregation (capturing the accumulator type) + its dispatch applier.
   */
  private static <ACC> SegmentMergingAggregation<DimensionKey, ACC> wire(
      final ActiveCube cube,
      final CompiledMeter meter,
      final DatasetWriter writer,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final TransactionRunner tx,
      final Map<Integer, CellApplier> byAggId) {
    @SuppressWarnings("unchecked")
    final BoundMeter<ACC, ?> bound = (BoundMeter<ACC, ?>) meter.bound();
    final DimensionKeyValue keyCodec = new DimensionKeyValue(cube.compiled().grain());
    final SegmentMergingAggregation<DimensionKey, ACC> merger =
        new SegmentMergingAggregation<>(
            meter.aggId(),
            bound.aggregate(),
            meter.windows(),
            new CubeServingSink<>(
                writer,
                cube.compiled(),
                meter.meterName(),
                meter.windowMs(),
                bound.accumulatorCodec()),
            cellStore,
            new DimensionKeyValue(cube.compiled().grain()),
            bound.accumulatorCodec(),
            tx);
    byAggId.put(
        meter.aggId(),
        (keyBytes, windowStart, accBytes) ->
            merger.merge(
                new Windowed<>(keyCodec.fromBytes(keyBytes), windowStart),
                bound.accumulatorCodec().fromBytes(accBytes)));
    return merger;
  }

  @Override
  public boolean ownsDurability() {
    return true;
  }

  @Override
  public long restore() {
    offsetKey.wrapInt(partition);
    return factsOffsets.get(offsetKey).map(DbLong::getValue).orElse(NO_OFFSET);
  }

  @Override
  public void process(final ShuffleEnvelope envelope) {
    if (envelope.payloadKind() != PayloadKind.AGGREGATE_DELTA
        || envelope.operation() != Operation.MERGE) {
      // Reference/upsert records are idempotent by key; the projected/reference path lands later.
      return;
    }
    if (!dedup.admit(envelope.producerPartition(), envelope.segment(), envelope.chunk())) {
      return; // a duplicate or producer re-emit of an already-merged batch
    }
    for (final CellDelta cell : envelope.cells()) {
      final CellApplier applier = byAggId.get(cell.streamId());
      if (applier != null) {
        applier.apply(cell.key(), cell.windowStart(), cell.payload());
      }
    }
  }

  @Override
  public void flush() {
    mergers.forEach(SegmentMergingAggregation::flush);
  }

  @Override
  public void commit(final long offset) {
    // Produce-before-commit: converge the sinks and make the serving rows durable first (the
    // serving
    // store is external to RocksDB), then persist the merged cells + the facts offset atomically.
    // The serving upsert is idempotent, so a crash between the two replays and re-merges
    // harmlessly.
    mergers.forEach(SegmentMergingAggregation::flush);
    servingWriter.flush();
    transactionRunner.runInTransaction(
        () -> {
          offsetKey.wrapInt(partition);
          offsetValue.wrapLong(offset);
          factsOffsets.put(offsetKey, offsetValue);
          mergers.forEach(SegmentMergingAggregation::checkpoint);
        });
  }

  @Override
  public void close() {
    try {
      datasetStore.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close serving store for facts partition {}", partition, e);
    }
    try {
      resource.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for facts partition {}", partition, e);
    }
  }
}
