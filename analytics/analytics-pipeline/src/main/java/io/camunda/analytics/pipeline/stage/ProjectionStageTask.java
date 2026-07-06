/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.aggregation.CubeMeterProcessor;
import io.camunda.analytics.aggregation.EnvelopePublisher;
import io.camunda.analytics.aggregation.ForwardingSegmentSink;
import io.camunda.analytics.aggregation.ShuffleSinkProcessor;
import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.projection.AnalyticsBaseProjection;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.analytics.state.StateBackedProjectionState;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.table.TableRowProcessor;
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
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One source partition's Stage-1 owning {@link Task}: it owns a per-partition RocksDB and drives a
 * declared {@link ProcessorTopology} — the base-projection {@link AnalyticsBaseProjection} ({@code
 * source}) fans facts to a {@link CubeMeterProcessor} per active cube-meter, each of which seals
 * and forwards {@code SegmentCell}s to a single shared {@link ShuffleSinkProcessor} node (the
 * transport), plus a {@link TableRowProcessor} per raw table. The base projection, every meter's
 * open segment and the consumed offset all live in the one provider, so {@link #commit(long)} makes
 * them one atomic cut (Model F): publish the sealed deltas (produce-before-commit), then persist
 * the <em>full</em> processed offset together with the topology's checkpoint. No {@code safeOffset}
 * — a crash resumes exactly from the committed offset onto the checkpointed open segments.
 *
 * <p><b>Live reload (ADR 0005).</b> The topology is built from the shared versioned {@link
 * DatasetCatalog}, not a frozen list. At each {@link #commit(long)} — after the durable cut, at
 * most once per reload-check interval — the task checks the catalog version; when it moved it
 * rebuilds its topology from the catalog's current cubes/tables <em>over the same open RocksDB</em>
 * (existing cubes' nodes recover their just-checkpointed state; a new cube's {@code aggId}-prefixed
 * families start empty). No ingestion pause, no re-seek, no RocksDB reopen.
 */
public final class ProjectionStageTask implements Task<SourceRecord>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ProjectionStageTask.class);

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
  private final EventBridgeClient client;
  private final String factsTopic;
  private final int factsPartitions;
  private final int segmentStride;
  private final int schemaVersion;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbBytes, DbBytes> openSegments;
  private final KeyValueStore<DbInt, DbLong> offsets;
  private final DatasetCatalog catalog;
  private final long reloadCheckIntervalMs;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();

  // Rebuilt on reload; the sealing aggregations are collected from the current topology so commit
  // can watermark-seal them. appliedVersion/lastReloadCheckMs drive the throttled reload check.
  private ProcessorTopology<SourceRecord> topology;
  private List<SegmentSealingAggregation<Fact, ?, ?>> sealingAggregations;
  private long appliedVersion;
  private long lastReloadCheckMs;

  ProjectionStageTask(
      final int partition,
      final EventBridgeClient client,
      final String factsTopic,
      final int factsPartitions,
      final int segmentStride,
      final int schemaVersion,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbBytes, DbBytes> openSegments,
      final KeyValueStore<DbInt, DbLong> offsets,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final long nowMs) {
    this.partition = partition;
    this.client = client;
    this.factsTopic = factsTopic;
    this.factsPartitions = factsPartitions;
    this.segmentStride = segmentStride;
    this.schemaVersion = schemaVersion;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.provider = provider;
    this.openSegments = openSegments;
    this.offsets = offsets;
    this.catalog = catalog;
    this.reloadCheckIntervalMs = reloadCheckIntervalMs;
    this.lastReloadCheckMs = nowMs;
    final DatasetCatalog.Snapshot snapshot = catalog.snapshot();
    installTopology(snapshot.cubes(), snapshot.tables());
    appliedVersion = snapshot.version();
  }

  public static ProjectionStageTask open(
      final int partition,
      final EventBridgeClient client,
      final String baseDir,
      final String factsTopic,
      final int factsPartitions,
      final int segmentStride,
      final int schemaVersion,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final DatasetStore datasetStore,
      final MeterRegistry meterRegistry) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(baseDir + "-p" + partition), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    return new ProjectionStageTask(
        partition,
        client,
        factsTopic,
        factsPartitions,
        segmentStride,
        schemaVersion,
        datasetStore,
        datasetStore.writer(),
        provider,
        openSegments,
        offsets,
        catalog,
        reloadCheckIntervalMs,
        System.currentTimeMillis());
  }

  /**
   * Builds the per-partition topology from the given cubes/tables over this task's reused provider,
   * open-segment store and serving writer, and collects the sealing aggregations. Called once at
   * construction and again on each live reload; the caller inits the returned topology.
   */
  private void installTopology(final List<ActiveCube> cubes, final List<ActiveTable> tables) {
    final StateBackedProjectionState state = StateBackedProjectionState.fromProvider(provider);
    final EnvelopePublisher publisher =
        new EnvelopePublisher(
            new EventBridgeEnvelopeTransport(client, factsTopic),
            schemaVersion,
            System.currentTimeMillis());

    // source → base projection; each cube-meter aggregate node seals and forwards SegmentCells to
    // one shared shuffle-sink node (the transport); each raw table writes rows to the serving
    // store.
    // The base projection broadcasts every fact to every child.
    final ProcessorTopology.Builder<SourceRecord> builder =
        ProcessorTopology.<SourceRecord>builder()
            .source("projection", new AnalyticsBaseProjection(state));
    final List<String> meterNodes = new ArrayList<>();
    final List<SegmentSealingAggregation<Fact, ?, ?>> aggregations = new ArrayList<>();
    for (final ActiveCube cube : cubes) {
      datasetStore.schemaManager().ensure(cube.compiled());
      for (final CompiledMeter meter : cube.compiled().meters()) {
        final String node = "meter-" + meter.aggId();
        builder.processor(
            node,
            meterProcessor(cube, meter, segmentStride, openSegments, provider, aggregations),
            "projection");
        meterNodes.add(node);
      }
    }
    if (!meterNodes.isEmpty()) {
      builder.processor(
          "shuffle",
          new ShuffleSinkProcessor(publisher, factsPartitions),
          meterNodes.toArray(new String[0]));
    }
    int tableIndex = 0;
    for (final ActiveTable table : tables) {
      datasetStore.schemaManager().ensureTable(table.compiled());
      builder.processor(
          "table-" + tableIndex++,
          new TableRowProcessor(table.registered(), table.compiled(), servingWriter),
          "projection");
    }
    // Process definitions take the direct path: a built-in raw table written straight to serving,
    // not a declared dataset. See ProcessDefinitionSink.
    datasetStore.schemaManager().ensureTable(ProcessDefinitionSink.TABLE);
    builder.processor(
        "process-definitions", new ProcessDefinitionSink(servingWriter), "projection");

    topology = builder.build();
    sealingAggregations = List.copyOf(aggregations);
  }

  /**
   * Builds one cube meter's Model-F sealing aggregation and its {@link ForwardingSegmentSink}
   * (capturing the acc type), then the node that gates + folds facts and forwards sealed cells. The
   * aggregation is also collected so the task can watermark-seal completed segments as it commits.
   */
  private static <ACC> CubeMeterProcessor meterProcessor(
      final ActiveCube cube,
      final CompiledMeter meter,
      final int segmentStride,
      final KeyValueStore<DbBytes, DbBytes> openSegments,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final List<SegmentSealingAggregation<Fact, ?, ?>> sealingAggregations) {
    @SuppressWarnings("unchecked")
    final BoundMeter<ACC, ?> bound = (BoundMeter<ACC, ?>) meter.bound();
    final ForwardingSegmentSink<ACC> sink =
        new ForwardingSegmentSink<>(
            meter.aggId(),
            new DimensionKeyValue(cube.compiled().grain()),
            bound.accumulatorCodec());
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
    sealingAggregations.add(sealing);
    return new CubeMeterProcessor(
        cube.compiled().factBinding().factType(),
        cube.registered(),
        cube.compiled().factBinding().filters(),
        sealing,
        sink);
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
    // Liveness: seal every segment the source has fully advanced past (the committed offset is the
    // watermark), so sparse cells — e.g. an incident meter that then goes quiet — reach the shuffle
    // even without a natural boundary crossing. The seal forwards SegmentCells into the shuffle
    // sink, so it must run before the flush below. See SegmentSealingAggregation#sealCompletedUpTo
    // for the at-least-once TODO on using the offset as the watermark.
    for (final SegmentSealingAggregation<Fact, ?, ?> aggregation : sealingAggregations) {
      aggregation.sealCompletedUpTo(offset);
    }
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
    maybeReload();
  }

  /**
   * At most once per reload-check interval, and only at this commit boundary (state + offset just
   * persisted), pick up a dataset-set change: refresh the shared catalog and, if its version moved,
   * rebuild the topology from its current cubes/tables over the same open RocksDB. The rebuilt
   * nodes recover their just-checkpointed state; a newly-declared cube starts empty and fills
   * forward.
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
    installTopology(snapshot.cubes(), snapshot.tables());
    topology.init();
    appliedVersion = snapshot.version();
    LOG.info(
        "Stage 1 partition {} reloaded topology at dataset-catalog version {}",
        partition,
        appliedVersion);
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
