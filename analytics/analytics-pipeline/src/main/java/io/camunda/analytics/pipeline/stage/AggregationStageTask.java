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
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.SegmentDedup;
import io.camunda.eventbridge.streaming.aggregate.SegmentDedup.StreamKey;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation;
import io.camunda.eventbridge.streaming.aggregate.SegmentPosition;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * drops them). The rebuild is incremental: surviving cubes keep their existing mergers (and their
 * in-heap cells — no re-recover), only an added cube's mergers are constructed (recovering empty)
 * and only its serving DDL is ensured, and a removed cube's wiring is dropped.
 */
public final class AggregationStageTask implements Task<ShuffleEnvelope>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AggregationStageTask.class);

  private final int partition;
  private final DatasetStore datasetStore;
  private final DatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbBytes, DbBytes> cellStore;
  private final KeyValueStore<DbInt, DbLong> offsets;
  private final KeyValueStore<DbBytes, DbBytes> dedupStore;
  private final DatasetCatalog catalog;
  private final long reloadCheckIntervalMs;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();
  private final DbBytes dedupKey = new DbBytes();
  private final DbBytes dedupValue = new DbBytes();

  /**
   * The dedup watermarks as last persisted, so each commit writes only the streams whose admission
   * watermark moved since the previous cut (the map itself is tiny — one entry per shuffle stream).
   */
  private final Map<StreamKey, SegmentPosition> persistedDedup = new HashMap<>();

  private ProcessorTopology<ShuffleEnvelope> topology;
  private long appliedVersion;
  private long lastReloadCheckMs;

  // Incremental-reload state (ADR 0005). A reload rebuilds only what changed: surviving meter
  // groups keep their wiring — appliers, mergers and the mergers' in-heap cells — so adding one
  // dataset never re-recover()s (prefix-scans) every other aggregation's durable state; a removed
  // cube's wiring is dropped (its durable cells stay, so a re-added id recovers from them alone);
  // and the serving DDL runs only for newly-added cubes. The dedup lives here rather than in the
  // rebuilt merge node so its admission watermarks also survive a reload.
  private final SegmentDedup dedup = new SegmentDedup();
  private final Map<Integer, MeterGroupWiring> wiringByStreamId = new HashMap<>();
  private Set<Long> appliedCubeIds = Set.of();

  AggregationStageTask(
      final int partition,
      final DatasetStore datasetStore,
      final DatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbInt, DbLong> offsets,
      final KeyValueStore<DbBytes, DbBytes> dedupStore,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final long nowMs) {
    this.partition = partition;
    this.datasetStore = datasetStore;
    this.servingWriter = servingWriter;
    this.provider = provider;
    this.cellStore = cellStore;
    this.offsets = offsets;
    this.dedupStore = dedupStore;
    // Restore the dedup's admission watermarks once, at task open — the one SegmentDedup instance
    // then survives every live reload, so a reload never forgets what was already admitted.
    dedupStore.forEach(
        (key, value) -> persistedDedup.put(decodeStreamKey(key), decodeSegmentPosition(value)));
    dedup.restore(persistedDedup);
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
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        provider.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    return new AggregationStageTask(
        partition,
        datasetStore,
        datasetStore.writer(),
        provider,
        cellStore,
        offsets,
        dedupStore,
        catalog,
        reloadCheckIntervalMs,
        System.currentTimeMillis());
  }

  /**
   * Builds the merge topology from the given cubes over this task's reused cell store and serving
   * writer: one {@link SegmentMergingAggregation} + dispatch applier per meter, behind a single
   * {@link CubeMergeProcessor}. Called once at construction and again on each live reload; the task
   * installs it into its own topology field and inits it.
   */
  private void installTopology(final List<ActiveCube> cubes) {
    final Map<Integer, CellApplier> byStreamId = new HashMap<>();
    final List<SegmentMergingAggregation<?, ?>> mergers = new ArrayList<>();
    final Set<Long> cubeIds = new HashSet<>();
    for (final ActiveCube cube : cubes) {
      cubeIds.add(cube.registered().cubeId());
      if (!appliedCubeIds.contains(cube.registered().cubeId())) {
        // Serving DDL only for a newly-added dataset — not for every dataset on every reload.
        datasetStore.schemaManager().ensure(cube.compiled());
      }
      // A meter's tiers share one shuffled (finest) stream; group them so the finest aggId's
      // applier can roll a delta up into every tier.
      final Map<String, List<CompiledMeter>> tiersByMeter = new LinkedHashMap<>();
      for (final CompiledMeter meter : cube.compiled().meters()) {
        tiersByMeter.computeIfAbsent(meter.meterName(), k -> new ArrayList<>()).add(meter);
      }
      for (final List<CompiledMeter> tiers : tiersByMeter.values()) {
        // Reuse a surviving meter group's wiring (its mergers keep their just-checkpointed heap
        // cells — no re-recover); construct (and recover) only a newly-added group's.
        final MeterGroupWiring wiring =
            wiringByStreamId.computeIfAbsent(
                finestOf(tiers).aggId(),
                streamId ->
                    wireMeterGroup(
                        cube, tiers, servingWriter, cellStore, provider::runInTransaction));
        byStreamId.put(finestOf(tiers).aggId(), wiring.applier());
        mergers.addAll(wiring.mergers());
      }
    }
    // Drop a removed cube's wiring: its heap state goes with it, while the durable cells remain —
    // a removed-then-readded id reconstructs above and recovers from durable state only.
    wiringByStreamId.keySet().retainAll(byStreamId.keySet());
    appliedCubeIds = Set.copyOf(cubeIds);
    topology =
        ProcessorTopology.<ShuffleEnvelope>builder()
            .source("merge", new CubeMergeProcessor(dedup, byStreamId, mergers))
            .build();
  }

  /** The finest tier of a meter group — its aggId is the stream Stage 1 shuffles. */
  private static CompiledMeter finestOf(final List<CompiledMeter> tiers) {
    CompiledMeter finest = tiers.get(0);
    for (final CompiledMeter meter : tiers) {
      if (meter.windowMs() < finest.windowMs()) {
        finest = meter;
      }
    }
    return finest;
  }

  /** One meter group's reusable wiring: the dispatch applier and its per-tier mergers. */
  private record MeterGroupWiring(
      CellApplier applier, List<SegmentMergingAggregation<?, ?>> mergers) {}

  /** A tier's merger paired with the window size a rolled-up delta aligns down to. */
  private record TierMerger<ACC>(
      long windowMs,
      SegmentMergingAggregation<DimensionKey, ACC> merger,
      BoundMeter<ACC, ?> bound) {}

  /**
   * Wires all tiers of one meter (capturing the acc type): a merger per tier writing its own
   * window-size cells, and a single dispatch applier on the finest tier's aggId — the only stream
   * Stage 1 shuffles — that folds each deduped delta into every tier, aligning its window start
   * down to the tier's window. A coarser cell is thus the exact merge of the finer deltas within
   * it.
   */
  private static <ACC> MeterGroupWiring wireMeterGroup(
      final ActiveCube cube,
      final List<CompiledMeter> tiers,
      final DatasetWriter writer,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final TransactionRunner tx) {
    final DimensionKeyValue keyCodec = new DimensionKeyValue(cube.compiled().grain());
    final List<SegmentMergingAggregation<?, ?>> mergers = new ArrayList<>();
    final List<TierMerger<ACC>> tierMergers = new ArrayList<>();
    for (final CompiledMeter meter : tiers) {
      @SuppressWarnings("unchecked")
      final BoundMeter<ACC, ?> bound = (BoundMeter<ACC, ?>) meter.bound();
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
      mergers.add(merger);
      tierMergers.add(new TierMerger<>(meter.windowMs(), merger, bound));
    }
    // One codec flyweight for the whole meter group (the applier runs on the task's single
    // thread), decoding each delta once through the merge-only path: the mergers fold a delta into
    // an accumulator they own and never store it, so all tiers can share one read-only view.
    @SuppressWarnings("unchecked")
    final RecordValue<ACC> deltaCodec = (RecordValue<ACC>) tiers.get(0).bound().accumulatorCodec();
    final CellApplier applier =
        (keyBytes, windowStart, accBytes) -> {
          final DimensionKey key = keyCodec.fromBytes(keyBytes);
          final ACC delta = deltaCodec.fromBytesForMerge(accBytes);
          for (final TierMerger<ACC> tier : tierMergers) {
            final long tierWindowStart = windowStart - Math.floorMod(windowStart, tier.windowMs());
            tier.merger().merge(new Windowed<>(key, tierWindowStart), delta);
          }
        };
    return new MeterGroupWiring(applier, List.copyOf(mergers));
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
          persistDedupWatermarks();
          topology.checkpoint();
        });
    maybeReload();
  }

  /**
   * Persists the dedup's admission watermarks — only the streams that moved since the last cut —
   * into the same transaction as the merged cells and the facts offset. Without this, a Stage-1
   * crash in its produce-before-commit gap re-publishes a segment delta as a <em>new</em>
   * facts-topic append, and a restarted (empty in-memory) dedup would re-admit and double-fold it.
   */
  private void persistDedupWatermarks() {
    for (final Map.Entry<StreamKey, SegmentPosition> watermark : dedup.snapshot().entrySet()) {
      if (watermark.getValue().equals(persistedDedup.get(watermark.getKey()))) {
        continue;
      }
      dedupKey.wrapBytes(encodeStreamKey(watermark.getKey()));
      dedupValue.wrapBytes(encodeSegmentPosition(watermark.getValue()));
      dedupStore.put(dedupKey, dedupValue);
      persistedDedup.put(watermark.getKey(), watermark.getValue());
    }
  }

  /** Dedup watermark key: {@code sourcePartition(4) ++ streamId(4)}, big-endian. */
  private static byte[] encodeStreamKey(final StreamKey key) {
    return ByteBuffer.allocate(2 * Integer.BYTES)
        .putInt(key.sourcePartition())
        .putInt(key.streamId())
        .array();
  }

  private static StreamKey decodeStreamKey(final DbBytes key) {
    final ByteBuffer buffer = ByteBuffer.wrap(key.getBytes());
    return new StreamKey(buffer.getInt(), buffer.getInt());
  }

  /** Dedup watermark value: {@code segment(8) ++ chunk(4)}, big-endian. */
  private static byte[] encodeSegmentPosition(final SegmentPosition position) {
    return ByteBuffer.allocate(Long.BYTES + Integer.BYTES)
        .putLong(position.segment())
        .putInt(position.chunk())
        .array();
  }

  private static SegmentPosition decodeSegmentPosition(final DbBytes value) {
    final ByteBuffer buffer = ByteBuffer.wrap(value.getBytes());
    return new SegmentPosition(buffer.getLong(), buffer.getInt());
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
