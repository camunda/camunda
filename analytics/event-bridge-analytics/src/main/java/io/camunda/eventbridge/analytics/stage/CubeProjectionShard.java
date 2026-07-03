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
import io.camunda.analytics.dataset.CubeMeterAggregation;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.AnalyticsFactProjector;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.ProjectionStage;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.Aggregation;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.aggregate.Segments;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One source partition's generic analytics shard (the declaration-driven replacement for {@code
 * ProjectionShard}): it owns a RocksDB provider for the base projection, folds each source record
 * once via {@link AnalyticsFactProjector} into generic facts, and fans them to a {@link
 * CubeMeterAggregation} per active cube-meter — each sealing segment deltas into the shared {@link
 * EnvelopePublisher}. A {@link Task} that owns its durability: {@link #restore()} resumes from its
 * committed position and {@link #commit(long)} publishes the sealed deltas then persists the base
 * projection + offset atomically.
 *
 * <p>The committed offset is {@code min(safeOffset)} over the cube meters — only past segments that
 * every meter has sealed — so a crash replays the open segments. TODO(e2e): the base projection
 * folds ahead of that safe offset; confirm the base-projection/segment checkpoint alignment (an
 * element activated before the safe offset and completed after it must re-fold correctly) during
 * the end-to-end run.
 */
public final class CubeProjectionShard implements Task<SourceRecord>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(CubeProjectionShard.class);

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
  private final StateBackedProjectionStore store;
  private final StreamProcessor<SourceRecord> processor;
  private final TransactionRunner transactionRunner;
  private final EnvelopePublisher publisher;
  private final List<CubeMeterAggregation> meters;
  private final AutoCloseable resource;

  CubeProjectionShard(
      final int partition,
      final StateBackedProjectionStore store,
      final StreamProcessor<SourceRecord> processor,
      final TransactionRunner transactionRunner,
      final EnvelopePublisher publisher,
      final List<CubeMeterAggregation> meters,
      final AutoCloseable resource) {
    this.partition = partition;
    this.store = store;
    this.processor = processor;
    this.transactionRunner = transactionRunner;
    this.publisher = publisher;
    this.meters = meters;
    this.resource = resource;
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
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final StateBackedProjectionStore store = StateBackedProjectionStore.fromProvider(provider);
    final AnalyticsFactProjector projector =
        new AnalyticsFactProjector(store, store.elementStarts());
    final EnvelopePublisher publisher =
        new EnvelopePublisher(
            new EventBridgeEnvelopeTransport(client, factsTopic),
            schemaVersion,
            System.currentTimeMillis());

    final List<CubeMeterAggregation> meters = new ArrayList<>();
    for (final ActiveCube cube : cubes) {
      for (final CompiledMeter meter : cube.compiled().meters()) {
        meters.add(meterAggregation(cube, meter, publisher, factsPartitions, segmentStride));
      }
    }

    final List<Aggregation<Fact>> aggregations = new ArrayList<>(meters);
    final StreamProcessor<SourceRecord> processor =
        new StreamProcessor<SourceRecord>().add(new ProjectionStage<>(projector, aggregations));
    return new CubeProjectionShard(
        partition, store, processor, provider::runInTransaction, publisher, meters, provider);
  }

  /** Builds one cube meter's sealing aggregation + shuffle sink, capturing the accumulator type. */
  private static <ACC> CubeMeterAggregation meterAggregation(
      final ActiveCube cube,
      final CompiledMeter meter,
      final EnvelopePublisher publisher,
      final int factsPartitions,
      final int segmentStride) {
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
        new SegmentSealingAggregation<Fact, DimensionKey, ACC>(
            bound.aggregate(),
            cube.compiled().keySelector(),
            COORDINATE,
            Fact::eventTime,
            meter.windows(),
            Segments.ofStride(segmentStride),
            sink);
    return new CubeMeterAggregation(
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
    processor.init();
  }

  @Override
  public long restore() {
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
  public void advanceStreamTime(final long streamTimeMs) {
    processor.advanceStreamTime(streamTimeMs);
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    processor.punctuateWallClock(wallClockMs);
  }

  @Override
  public boolean needsCheckpoint() {
    return processor.needsCheckpoint();
  }

  @Override
  public void commit(final long offset) {
    // Produce-before-commit: publish sealed deltas, then persist the base projection + the safe
    // offset (only past segments every meter has sealed) atomically. The runtime's offset is
    // ignored in favour of the segment-safe offset so a crash replays the open segments.
    publisher.flush();
    final long safe =
        meters.stream()
            .mapToLong(CubeMeterAggregation::safeOffset)
            .filter(position -> position >= 0)
            .min()
            .orElse(-1L);
    if (safe >= 0) {
      transactionRunner.runInTransaction(
          () -> {
            store.setConsumedPosition(partition, safe);
            processor.checkpoint();
          });
    }
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
