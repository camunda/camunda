/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.aggregation.CubeMergeProcessor;
import io.camunda.analytics.aggregation.CubeMergeProcessor.CellApplier;
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
 */
public final class AggregationStageTask implements Task<ShuffleEnvelope>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AggregationStageTask.class);

  private final int partition;
  private final ProcessorTopology<ShuffleEnvelope> topology;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbInt, DbLong> offsets;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  AggregationStageTask(
      final int partition,
      final ProcessorTopology<ShuffleEnvelope> topology,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbInt, DbLong> offsets) {
    this.partition = partition;
    this.topology = topology;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.provider = provider;
    this.offsets = offsets;
  }

  public static AggregationStageTask open(
      final int partition,
      final String baseDir,
      final DatasetStore datasetStore,
      final List<ActiveCube> cubes,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DatasetWriter writer = datasetStore.writer();

    final Map<Integer, CellApplier> byStreamId = new HashMap<>();
    final List<SegmentMergingAggregation<?, ?>> mergers = new ArrayList<>();
    for (final ActiveCube cube : cubes) {
      datasetStore.schemaManager().ensure(cube.compiled());
      for (final CompiledMeter meter : cube.compiled().meters()) {
        mergers.add(wire(cube, meter, writer, cellStore, provider::runInTransaction, byStreamId));
      }
    }
    final ProcessorTopology<ShuffleEnvelope> topology =
        ProcessorTopology.<ShuffleEnvelope>builder()
            .source("merge", new CubeMergeProcessor(new SegmentDedup(), byStreamId, mergers))
            .build();
    return new AggregationStageTask(partition, topology, datasetStore, writer, provider, offsets);
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
