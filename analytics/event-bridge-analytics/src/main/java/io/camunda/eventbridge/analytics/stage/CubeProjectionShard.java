/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.aggregation.CubeMeterProcessor;
import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveProjection;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.projection.AnalyticsBaseProjection;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.StateBackedProjectionState;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.aggregate.Segments;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.processor.ProcessorTopology;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One source partition's Stage-1 owning {@link Task}: it owns a per-partition RocksDB and drives a
 * declared {@link ProcessorTopology} — the base-projection {@link AnalyticsBaseProjection} ({@code
 * source}) fanning facts to a {@link CubeMeterProcessor} per active cube-meter and a {@link
 * ProjectionRowProcessor} per projected dataset. The base projection, every meter's open segment
 * and the consumed offset all live in the one provider, so {@link #commit(long)} makes them one
 * atomic cut (Model F): publish the sealed deltas (produce-before-commit), then persist the
 * <em>full</em> processed offset together with the topology's checkpoint. No {@code safeOffset} — a
 * crash resumes exactly from the committed offset onto the checkpointed open segments.
 */
public final class CubeProjectionShard implements Task<SourceRecord>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(CubeProjectionShard.class);

  /** Source coordinate of a fact — the origin the shuffle dedups by. */
  private static final SourceCoordinate<Fact> COORDINATE =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Fact fact) {
          return fact.sourcePartition();
        }

        @Override
        public long position(final Fact fact) {
          return fact.sourcePosition();
        }
      };

  private final int partition;
  private final ProcessorTopology<SourceRecord> topology;
  private final EnvelopePublisher publisher;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbInt, DbLong> offsets;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  CubeProjectionShard(
      final int partition,
      final ProcessorTopology<SourceRecord> topology,
      final EnvelopePublisher publisher,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbInt, DbLong> offsets) {
    this.partition = partition;
    this.topology = topology;
    this.publisher = publisher;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.provider = provider;
    this.offsets = offsets;
  }

  public static CubeProjectionShard open(
      final int partition,
      final EventBridgeClient client,
      final String baseDir,
      final String factsTopic,
      final int factsPartitions,
      final int segmentStride,
      final int schemaVersion,
      final List<ActiveCube> cubes,
      final List<ActiveProjection> projections,
      final DatasetStore datasetStore,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final StateBackedProjectionState state = StateBackedProjectionState.fromProvider(provider);
    final EnvelopePublisher publisher =
        new EnvelopePublisher(
            new EventBridgeEnvelopeTransport(client, factsTopic),
            schemaVersion,
            System.currentTimeMillis());
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DatasetWriter writer = datasetStore.writer();

    // source → base projection; children → one aggregate node per cube-meter + one row node per
    // projected dataset (the base projection broadcasts each fact to every child).
    final ProcessorTopology.Builder<SourceRecord> builder =
        ProcessorTopology.<SourceRecord>builder()
            .source("projection", new AnalyticsBaseProjection(state));
    for (final ActiveCube cube : cubes) {
      for (final CompiledMeter meter : cube.compiled().meters()) {
        builder.processor(
            "meter-" + meter.aggId(),
            meterProcessor(
                cube, meter, publisher, factsPartitions, segmentStride, openSegments, provider),
            "projection");
      }
    }
    int projectionIndex = 0;
    for (final ActiveProjection projection : projections) {
      datasetStore.schemaManager().ensureProjection(projection.compiled());
      builder.processor(
          "projection-" + projectionIndex++,
          new ProjectionRowProcessor(projection.registered(), projection.compiled(), writer),
          "projection");
    }
    return new CubeProjectionShard(
        partition, builder.build(), publisher, datasetStore, writer, provider, offsets);
  }

  /** Builds one cube meter's Model-F sealing aggregation + shuffle sink, capturing the acc type. */
  private static <ACC> CubeMeterProcessor meterProcessor(
      final ActiveCube cube,
      final CompiledMeter meter,
      final EnvelopePublisher publisher,
      final int factsPartitions,
      final int segmentStride,
      final KeyValueStore<DbBytes, DbBytes> openSegments,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider) {
    @SuppressWarnings("unchecked")
    final BoundMeter<ACC, ?> bound = (BoundMeter<ACC, ?>) meter.bound();
    final CubeShuffleSink<ACC> sink =
        new CubeShuffleSink<>(
            meter.aggId(),
            new DimensionKeyValue(cube.compiled().grain()),
            bound.accumulatorCodec(),
            publisher,
            factsPartitions);
    final SegmentSealingAggregation<Fact, DimensionKey, ACC> sealing =
        new SegmentSealingAggregation<>(
            meter.aggId(),
            bound.aggregate(),
            cube.compiled().keySelector(),
            COORDINATE,
            Fact::eventTime,
            meter.windows(),
            Segments.ofStride(segmentStride),
            sink,
            openSegments,
            new DimensionKeyValue(cube.compiled().grain()),
            bound.accumulatorCodec(),
            provider::runInTransaction);
    return new CubeMeterProcessor(
        cube.compiled().factBinding().factType(),
        cube.registered(),
        cube.compiled().factBinding().filters(),
        sealing);
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
  public void process(final SourceRecord record) {
    topology.process(record);
  }

  @Override
  public void flush() {
    topology.flush();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    topology.advanceStreamTime(streamTimeMs);
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    topology.punctuateWallClock(wallClockMs);
  }

  @Override
  public boolean needsCheckpoint() {
    return topology.needsCheckpoint();
  }

  @Override
  public void commit(final long offset) {
    // Produce-before-commit: publish the sealed shuffle deltas and flush the projected rows, then
    // persist the full offset + the topology's state (base projection + every open segment) as one
    // atomic cut on this partition's provider.
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
      LOG.warn("Failed to close serving store for partition {}", partition, e);
    }
    try {
      provider.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for partition {}", partition, e);
    }
  }
}
