/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.aggregation.CubeMergeProcessor;
import io.camunda.analytics.aggregation.CubeMergeProcessor.CellApplier;
import io.camunda.analytics.aggregation.CubeServingSink;
import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.SegmentDedup;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation;
import io.camunda.eventbridge.streaming.processor.ProcessorTopology;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
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
 * One facts-topic partition's Stage-2 owning {@link Task}: it owns a per-partition RocksDB and
 * drives a one-node {@link ProcessorTopology} whose source is a {@link CubeMergeProcessor} — dedup
 * each envelope, dispatch its cell deltas by {@code streamId} to the matching meter's {@link
 * SegmentMergingAggregation}, converge the idempotent serving sink. The merged cells and the facts
 * offset live in the one provider, so {@link #commit(long)} makes them one atomic cut: converge the
 * sinks and flush the serving rows (produce-before-commit), then persist the offset + merged cells.
 *
 * <p><b>Live reload (ADR 0005).</b> The merge topology is built from the shared versioned {@link
 * DatasetCatalog}. At each {@link #commit(long)} — after the durable cut, at most once per
 * reload-check interval — the task checks the catalog version and, when it moved, rebuilds the
 * merge node from the catalog's current cubes <em>over the same open RocksDB</em> so it has a
 * merger + {@code CellApplier} for a newly-declared cube's {@code aggId}s (otherwise the merge node
 * drops them). Existing cubes' merged cells recover from the just-checkpointed store; the new
 * cube's start empty.
 */
public final class AggregationStageTask implements Task<ShuffleEnvelope>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AggregationStageTask.class);

  private final int partition;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbBytes, DbBytes> cellStore;
  private final KeyValueStore<DbInt, DbLong> offsets;
  private final DatasetCatalog catalog;
  private final long reloadCheckIntervalMs;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  private ProcessorTopology<ShuffleEnvelope> topology;
  private long appliedVersion;
  private long lastReloadCheckMs;

  AggregationStageTask(
      final int partition,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbInt, DbLong> offsets,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final long nowMs) {
    this.partition = partition;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.provider = provider;
    this.cellStore = cellStore;
    this.offsets = offsets;
    this.catalog = catalog;
    this.reloadCheckIntervalMs = reloadCheckIntervalMs;
    this.lastReloadCheckMs = nowMs;
    final DatasetCatalog.Snapshot snapshot = catalog.snapshot();
    installTopology(snapshot.cubes());
    appliedVersion = snapshot.version();
  }

  public static AggregationStageTask open(
      final int partition,
      final String baseDir,
      final DatasetStore datasetStore,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    return new AggregationStageTask(
        partition,
        datasetStore,
        datasetStore.writer(),
        provider,
        cellStore,
        offsets,
        catalog,
        reloadCheckIntervalMs,
        System.currentTimeMillis());
  }

  /**
   * Builds the merge topology from the given cubes over this task's reused cell store and serving
   * writer: one {@link SegmentMergingAggregation} + dispatch applier per meter, behind a single
   * {@link CubeMergeProcessor}. Called once at construction and again on each live reload; the
   * caller inits the returned topology.
   */
  private void installTopology(final List<ActiveCube> cubes) {
    final Map<Integer, CellApplier> byStreamId = new HashMap<>();
    final List<SegmentMergingAggregation<?, ?>> mergers = new ArrayList<>();
    for (final ActiveCube cube : cubes) {
      datasetStore.schemaManager().ensure(cube.compiled());
      for (final CompiledMeter meter : cube.compiled().meters()) {
        mergers.add(
            wire(cube, meter, servingWriter, cellStore, provider::runInTransaction, byStreamId));
      }
    }
    topology =
        ProcessorTopology.<ShuffleEnvelope>builder()
            .source("merge", new CubeMergeProcessor(new SegmentDedup(), byStreamId, mergers))
            .build();
  }

  /** Wires one meter's merging aggregation (capturing the acc type) + its dispatch applier. */
  private static <ACC> SegmentMergingAggregation<DimensionKey, ACC> wire(
      final ActiveCube cube,
      final CompiledMeter meter,
      final DatasetWriter writer,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final TransactionRunner tx,
      final Map<Integer, CellApplier> byStreamId) {
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
    byStreamId.put(
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
  public void init() {
    topology.init();
  }

  @Override
  public long restore() {
    offsetKey.wrapInt(partition);
    return offsets.get(offsetKey).map(DbLong::getValue).orElse(NO_OFFSET);
  }

  @Override
  public void process(final ShuffleEnvelope envelope) {
    topology.process(envelope);
  }

  @Override
  public void flush() {
    topology.flush();
  }

  @Override
  public void commit(final long offset) {
    // Produce-before-commit: converge the sinks and flush the (idempotent) serving rows, then
    // persist the facts offset + the merged cells as one atomic cut on this partition's provider.
    topology.flush();
    servingWriter.flush();
    provider.runInTransaction(
        () -> {
          offsetKey.wrapInt(partition);
          offsetValue.wrapLong(offset);
          offsets.put(offsetKey, offsetValue);
          topology.checkpoint();
        });
    maybeReload();
  }

  /**
   * At most once per reload-check interval, and only at this commit boundary (state + offset just
   * persisted), pick up a dataset-set change: refresh the shared catalog and, if its version moved,
   * rebuild the merge node from its current cubes over the same open RocksDB so a newly-declared
   * cube's {@code aggId} deltas are merged rather than dropped.
   */
  private void maybeReload() {
    final long now = System.currentTimeMillis();
    if (now - lastReloadCheckMs < reloadCheckIntervalMs) {
      return;
    }
    lastReloadCheckMs = now;
    catalog.refresh();
    final DatasetCatalog.Snapshot snapshot = catalog.snapshot();
    if (snapshot.version() == appliedVersion) {
      return;
    }
    installTopology(snapshot.cubes());
    topology.init();
    appliedVersion = snapshot.version();
    LOG.info(
        "Stage 2 facts partition {} reloaded merge topology at dataset-catalog version {}",
        partition,
        appliedVersion);
  }

  @Override
  public void close() {
    topology.close();
    try {
      datasetStore.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close serving store for facts partition {}", partition, e);
    }
    try {
      provider.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for facts partition {}", partition, e);
    }
  }
}
