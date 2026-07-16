/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.aggregation.CubeAggregationProcessor;
import io.camunda.analytics.aggregation.EnvelopePublisher;
import io.camunda.analytics.aggregation.FactTypeDispatcher;
import io.camunda.analytics.aggregation.ForwardingSegmentSink;
import io.camunda.analytics.aggregation.ShuffleSinkProcessor;
import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.projection.AnalyticsBaseProjection;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.state.StateBackedProjectionState;
import io.camunda.analytics.state.VariableNames;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.table.TableRowProcessor;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.OwnershipEpoch;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.aggregate.Segments;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.internals.FlowMetrics;
import io.camunda.eventbridge.streaming.internals.StoreMetrics;
import io.camunda.eventbridge.streaming.processor.ProcessorTopology;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.StoreTuning;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One source partition's Stage-1 owning {@link Task}: it owns a per-partition RocksDB and drives a
 * declared {@link ProcessorTopology} — the base-projection {@link AnalyticsBaseProjection} ({@code
 * source}) fans facts through a {@link FactTypeDispatcher} (routing each fact only to the nodes
 * whose bound fact type matches) to a {@link CubeAggregationProcessor} per active cube, each of
 * which seals and forwards {@code SegmentCell}s to a single shared {@link ShuffleSinkProcessor}
 * node (the transport), plus a {@link TableRowProcessor} per raw table. The base projection, every
 * meter's open segment and the consumed offset all live in the one provider, so {@link
 * #freezeCut(long)} makes them one atomic cut (Model F): publish the sealed deltas
 * (produce-before-commit), then persist the <em>full</em> processed offset together with the
 * topology's state. No {@code safeOffset} — a crash resumes exactly from the committed offset onto
 * the checkpointed open segments.
 *
 * <p><b>Frozen cuts (streaming ADR 0005, 0008).</b> {@link #freezeCut(long)} detaches the cut at
 * the barrier — the watermark seal, the encoded shuffle frames, the staged serving rows, the
 * at-barrier pre-fold dedup watermarks and every store's frozen overlay — and the runtime drives
 * publish → persist → complete: on an IO thread while the partition keeps folding, or inline on the
 * actor thread for the final cut at shutdown.
 *
 * <p><b>Live reload (ADR 0005).</b> The topology is built from the shared versioned {@link
 * DatasetCatalog}, not a frozen list. On each successful cut's completion — after the durable cut,
 * at most once per reload-check interval — the task checks the catalog version; when it moved it
 * rebuilds its topology from the catalog's current cubes/tables <em>over the same open RocksDB</em>
 * — incrementally: surviving cubes keep their nodes and sealing aggregations (with their in-heap
 * open segments — no re-recover), only a newly-declared cube's meters are constructed (starting
 * empty) and only its serving DDL is ensured, and a removed cube's wiring is dropped. No ingestion
 * pause, no re-seek, no RocksDB reopen.
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
  private final OwnershipEpoch epoch;
  private final EventBridgeClient client;
  private final String factsTopic;
  private final int factsPartitions;
  private final int segmentStride;
  private final Segments segments;
  private final int schemaVersion;
  private final DatasetStore datasetStore;
  private final FreezableDatasetWriter servingWriter;
  private final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private final KeyValueStore<DbBytes, DbBytes> openSegments;
  private final KeyValueStore<DbInt, DbLong> offsets;
  private final KeyValueStore<DbInt, DbLong> appliedPositions;
  private final DatasetCatalog catalog;
  private final long reloadCheckIntervalMs;
  private final boolean eagerShufflePublish;
  private final ProjectionMetrics metrics;
  private final FlowMetrics flowMetrics;
  private final StoreMetrics storeMetrics;

  private final DbInt offsetKey = new DbInt();
  private final DbLong offsetValue = new DbLong();
  private final DbInt appliedKey = new DbInt();
  private final DbLong appliedValue = new DbLong();

  /**
   * Pre-fold dedup (ADR 0007): the high-watermark of the last applied Zeebe record position per
   * Zeebe partition. The exporter appends each Zeebe partition's records in non-decreasing position
   * order and a retry only re-appends positions at-or-below what was already appended, so a record
   * at-or-below its partition's watermark is a producer duplicate and is skipped before the fold.
   * Heap-authoritative between commits; persisted into {@link #appliedPositions} inside the same
   * atomic cut as the topology state and the consumed offset.
   */
  private final Map<Integer, Long> appliedWatermarks = new HashMap<>();

  // Rebuilt on reload; the sealing aggregations are collected from the current topology so commit
  // can watermark-seal them, and the projection state is kept so the frozen cut can drive its
  // freeze/persist/complete split directly. appliedVersion/lastReloadCheckMs drive the throttled
  // reload check.
  private ProcessorTopology<SourceRecord> topology;
  private List<SegmentSealingAggregation<Fact, ?, ?>> sealingAggregations;
  private StateBackedProjectionState projectionState;
  private EnvelopePublisher publisher;
  private long appliedVersion;
  private long lastReloadCheckMs;

  // Incremental-reload state (ADR 0005). A reload rebuilds only what changed: a surviving meter
  // keeps its node + sealing aggregation (and the aggregation's in-heap open segment — no
  // re-recover prefix scan), only an added cube's meters are constructed and only its serving DDL
  // is ensured; a removed cube's wiring is dropped so a re-added id reconstructs and recovers from
  // its durable open segment alone. Tables hold no state and are rebuilt, but their DDL is scoped
  // the same way.
  private final Map<Integer, CubeWiring> wiringByStreamId = new HashMap<>();
  private Set<Long> appliedCubeIds = Set.of();
  private Set<Long> appliedTableIds = Set.of();
  private boolean definitionsTableEnsured;

  // Per-task meter deregistrations run at close (see disposeOnClose): a re-opened partition task
  // re-registers meters under the same ids, and Micrometer keeps the first — without the removal
  // the dead task's meters would shadow the live one's forever.
  private final List<Runnable> meterDisposals = new ArrayList<>();

  ProjectionStageTask(
      final int partition,
      final OwnershipEpoch epoch,
      final EventBridgeClient client,
      final String factsTopic,
      final int factsPartitions,
      final int segmentStride,
      final int schemaVersion,
      final DatasetStore datasetStore,
      final VersionedDatasetWriter servingWriter,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final KeyValueStore<DbBytes, DbBytes> openSegments,
      final KeyValueStore<DbInt, DbLong> offsets,
      final KeyValueStore<DbInt, DbLong> appliedPositions,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final boolean eagerShufflePublish,
      final ProjectionMetrics metrics,
      final FlowMetrics flowMetrics,
      final StoreMetrics storeMetrics,
      final long nowMs) {
    this.partition = partition;
    this.epoch = epoch;
    this.client = client;
    this.factsTopic = factsTopic;
    this.factsPartitions = factsPartitions;
    this.segmentStride = segmentStride;
    segments = Segments.ofStride(segmentStride);
    this.schemaVersion = schemaVersion;
    this.datasetStore = datasetStore;
    // Stage the serving writes on the heap so a frozen cut flushes exactly the rows its barrier
    // covers while the actor keeps folding (and writing) past it. Each cut seals its batch with
    // (ownership epoch, cut offset) — the write fence the backend enforces.
    this.servingWriter =
        new FreezableDatasetWriter(servingWriter, new WriteVersion(epoch.current(), 0));
    this.provider = provider;
    this.openSegments = openSegments;
    this.offsets = offsets;
    this.appliedPositions = appliedPositions;
    appliedPositions.forEach(
        (key, value) -> appliedWatermarks.put(key.getValue(), value.getValue()));
    this.catalog = catalog;
    this.reloadCheckIntervalMs = reloadCheckIntervalMs;
    this.eagerShufflePublish = eagerShufflePublish;
    this.metrics = metrics;
    this.flowMetrics = flowMetrics;
    this.storeMetrics = storeMetrics;
    this.lastReloadCheckMs = nowMs;
    final DatasetCatalog.Snapshot snapshot = catalog.snapshot();
    installTopology(snapshot.cubes(), snapshot.tables());
    appliedVersion = snapshot.version();
    // Bind the overlay gauges ONCE, over suppliers that read the projectionState FIELD: a live
    // catalog reload rebuilds the base state's caches, and a gauge bound to one generation would
    // silently keep reading (and strongly pin) the abandoned caches forever — Micrometer keeps the
    // first registration under an id. The task's close() deregisters them so a re-opened partition
    // task's fresh binding is not ignored either.
    for (final String store : StateBackedProjectionState.storeNames()) {
      storeMetrics.bindOverlay(
          store,
          () -> projectionState.overlayEntries(store),
          () -> projectionState.overlayBytes(store));
    }
  }

  public static ProjectionStageTask open(
      final int partition,
      final OwnershipEpoch epoch,
      final EventBridgeClient client,
      final String baseDir,
      final String factsTopic,
      final int factsPartitions,
      final int segmentStride,
      final int schemaVersion,
      final DatasetCatalog catalog,
      final long reloadCheckIntervalMs,
      final boolean eagerShufflePublish,
      final DatasetStore datasetStore,
      final MeterRegistry meterRegistry,
      final StoreTuning storeTuning) {
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(
            new File(baseDir + "-p" + partition), meterRegistry, storeTuning);
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbInt, DbLong> appliedPositions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong());
    final VersionedDatasetWriter servingWriter = datasetStore.writer();
    // The write fence's observability: rejections are the fence working (zero outside
    // rebalances/replays), so they are exposed as a counter rather than logged as errors.
    final FunctionCounter fencedWrites =
        FunctionCounter.builder(
                "analytics.serving.fenced.writes",
                servingWriter,
                VersionedDatasetWriter::fencedWrites)
            .description("Serving writes rejected by the version fence (stale by arrival)")
            .tag("stage", "projection")
            .tag("partition", String.valueOf(partition))
            .register(meterRegistry);
    final ProjectionStageTask task =
        new ProjectionStageTask(
            partition,
            epoch,
            client,
            factsTopic,
            factsPartitions,
            segmentStride,
            schemaVersion,
            datasetStore,
            servingWriter,
            provider,
            openSegments,
            offsets,
            appliedPositions,
            catalog,
            reloadCheckIntervalMs,
            eagerShufflePublish,
            new MicrometerProjectionMetrics(meterRegistry, partition),
            FlowMetrics.of(meterRegistry, "projection", partition),
            StoreMetrics.of(meterRegistry, "projection", partition),
            System.currentTimeMillis());
    // Deregistered at close: a re-opened partition task registers its own FunctionCounter over its
    // own writer, which Micrometer would otherwise ignore in favor of the closed task's (weakly
    // referenced) one — the counter would silently freeze, then read nothing.
    task.disposeOnClose(() -> meterRegistry.remove(fencedWrites));
    return task;
  }

  /**
   * Builds the per-partition topology from the given cubes/tables over this task's reused provider,
   * open-segment store and serving writer, and collects the sealing aggregations. Called once at
   * construction and again on each live reload; it installs into the task's own topology field.
   */
  /**
   * The union of {@code var.*} names any active dataset groups or filters by (grain dimension
   * columns + filter predicates, across cubes and raw tables), stripped of the {@code var.} prefix
   * — exactly the variables the enrichment must resolve. Both dimensions and filters are collected
   * so a dataset that only <em>filters</em> by a variable still gets it resolved.
   */
  private static Set<String> variableNames(
      final List<ActiveCube> cubes, final List<ActiveTable> tables) {
    final Set<String> names = new HashSet<>();
    for (final ActiveCube cube : cubes) {
      cube.compiled().grain().columns().forEach(c -> addVariableName(names, c.name()));
      cube.compiled().factBinding().filters().forEach(f -> addVariableName(names, f.field()));
    }
    for (final ActiveTable table : tables) {
      table.compiled().columns().forEach(c -> addVariableName(names, c.name()));
      table.compiled().factBinding().filters().forEach(f -> addVariableName(names, f.field()));
    }
    return names;
  }

  private static void addVariableName(final Set<String> names, final String field) {
    if (field.startsWith(DimensionSpec.VARIABLE_PREFIX)) {
      names.add(field.substring(DimensionSpec.VARIABLE_PREFIX.length()));
    }
  }

  private void installTopology(final List<ActiveCube> cubes, final List<ActiveTable> tables) {
    final StateBackedProjectionState state = StateBackedProjectionState.fromProvider(provider);
    // No gauge binding here: the overlay gauges are bound once at construction over suppliers
    // that read the projectionState field, so re-pointing it below is all a reload needs to do
    // for the gauges to track the new generation.
    projectionState = state;
    final EnvelopePublisher publisher =
        new EnvelopePublisher(
            new EventBridgeEnvelopeTransport(client, factsTopic),
            schemaVersion,
            System.currentTimeMillis(),
            eagerShufflePublish);
    this.publisher = publisher;

    // source → base projection → fact-type dispatch; each cube-meter aggregate node seals and
    // forwards SegmentCells to one shared shuffle-sink node (the transport); each raw table writes
    // rows to the serving store.
    // The dispatch node routes each fact only to the children whose bound fact type matches
    // (compiled at install time), instead of broadcasting every fact to every node — the nodes'
    // own gates are unchanged, they just no longer see the facts they would reject by type.
    // The union of var.* names any active dataset groups or filters by — so the base projection's
    // variable enrichment resolves only those names (point lookups, early-terminating) instead of
    // scanning the whole scope. Recomputed on each catalog reload.
    final VariableNames variableNames = VariableNames.of(variableNames(cubes, tables));
    final FactTypeDispatcher dispatcher = new FactTypeDispatcher(metrics);
    final ProcessorTopology.Builder<SourceRecord> builder =
        ProcessorTopology.<SourceRecord>builder()
            .source("projection", new AnalyticsBaseProjection(state, variableNames, metrics))
            .processor("dispatch", dispatcher, "projection");
    final List<String> meterNodes = new ArrayList<>();
    final List<SegmentSealingAggregation<Fact, ?, ?>> aggregations = new ArrayList<>();
    final Set<Long> cubeIds = new HashSet<>();
    final Set<Integer> activeStreamIds = new HashSet<>();
    for (final ActiveCube cube : cubes) {
      cubeIds.add(cube.registered().cubeId());
      if (!appliedCubeIds.contains(cube.registered().cubeId())) {
        // Serving DDL only for a newly-added dataset — not for every dataset on every reload.
        datasetStore.schemaManager().ensure(cube.compiled());
      }
      // One composite fold per cube (ADR 0009): every meter is a slot of the one accumulator, so
      // the gate and the key extraction run once per fact per dataset, and the shuffle carries
      // one stream per cube. Only the finest tier is aggregated and shipped; Stage 2 rolls it up.
      final int streamId = cube.compiled().streamId();
      final String node = "cube-" + streamId;
      // Reuse a surviving cube's wiring (its aggregation keeps the just-checkpointed open
      // segment on the heap — no re-recover prefix scan); construct only a newly-added one's.
      final CubeWiring wiring =
          wiringByStreamId.computeIfAbsent(
              streamId,
              id -> {
                final CubeWiring created =
                    cubeWiring(cube, segmentStride, openSegments, provider, flowMetrics, metrics);
                // Expose the new cube's gate counters + silent-empty alarm (once per wiring).
                metrics.registerCubeGate(created.processor());
                return created;
              });
      activeStreamIds.add(streamId);
      aggregations.add(wiring.aggregation());
      builder.processor(node, wiring.processor(), "dispatch");
      dispatcher.route(node, wiring.processor().factType());
      meterNodes.add(node);
    }
    // Drop a removed cube's wiring: its heap state goes with it, while the durable open segment
    // remains — a removed-then-readded id reconstructs above and recovers from durable state only.
    wiringByStreamId.keySet().retainAll(activeStreamIds);
    if (!meterNodes.isEmpty()) {
      builder.processor(
          "shuffle",
          new ShuffleSinkProcessor(publisher, factsPartitions),
          meterNodes.toArray(new String[0]));
    }
    int tableIndex = 0;
    final Set<Long> tableIds = new HashSet<>();
    for (final ActiveTable table : tables) {
      tableIds.add(table.registered().cubeId());
      if (!appliedTableIds.contains(table.registered().cubeId())) {
        datasetStore.schemaManager().ensureTable(table.compiled());
      }
      final String node = "table-" + tableIndex++;
      final TableRowProcessor processor =
          new TableRowProcessor(table.registered(), table.compiled(), servingWriter);
      builder.processor(node, processor, "dispatch");
      dispatcher.route(node, processor.factType());
    }
    // Process definitions take the direct path: a built-in raw table written straight to serving,
    // not a declared dataset. See ProcessDefinitionSink. Its fixed schema is ensured once.
    if (!definitionsTableEnsured) {
      datasetStore.schemaManager().ensureTable(ProcessDefinitionSink.TABLE);
      definitionsTableEnsured = true;
    }
    final ProcessDefinitionSink definitionSink = new ProcessDefinitionSink(servingWriter);
    builder.processor("process-definitions", definitionSink, "dispatch");
    dispatcher.route("process-definitions", definitionSink.factType());

    appliedCubeIds = Set.copyOf(cubeIds);
    appliedTableIds = Set.copyOf(tableIds);
    topology = builder.build();
    sealingAggregations = List.copyOf(aggregations);
  }

  /** One cube's reusable wiring: its gate/fold node and its composite sealing aggregation. */
  private record CubeWiring(
      CubeAggregationProcessor processor, SegmentSealingAggregation<Fact, ?, ?> aggregation) {}

  /**
   * Builds one cube's Model-F composite sealing aggregation and its {@link ForwardingSegmentSink},
   * then the node that gates + folds facts and forwards sealed cells (ADR 0009: one fold, one
   * stream per cube — every meter is a slot of the composite accumulator). The aggregation is also
   * part of the wiring so the task can watermark-seal completed segments as it commits.
   */
  private static CubeWiring cubeWiring(
      final ActiveCube cube,
      final int segmentStride,
      final KeyValueStore<DbBytes, DbBytes> openSegments,
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider,
      final FlowMetrics flowMetrics,
      final ProjectionMetrics metrics) {
    final CompiledDataset compiled = cube.compiled();
    final List<BoundMeter<?, ?>> bounds = compiled.meterBounds();
    final ForwardingSegmentSink<Object[]> sink =
        new ForwardingSegmentSink<>(
            compiled.streamId(),
            new DimensionKeyValue(compiled.grain()),
            new CompositeAccumulatorValue(bounds));
    final SegmentSealingAggregation<Fact, DimensionKey, Object[]> sealing =
        new SegmentSealingAggregation<>(
            compiled.streamId(),
            new CompositeAggregateFunction(bounds),
            new DimensionKeySelector(compiled.grain()),
            COORDINATE,
            Fact::eventTime,
            compiled.finestTier().windows(),
            Segments.ofStride(segmentStride),
            sink,
            openSegments,
            new DimensionKeyValue(compiled.grain()),
            new CompositeAccumulatorValue(bounds),
            provider::runInTransaction);
    sealing.metrics(flowMetrics);
    return new CubeWiring(
        new CubeAggregationProcessor(
            compiled.factBinding().factType(),
            cube.registered(),
            compiled.factBinding().filters(),
            sealing,
            sink,
            metrics),
        sealing);
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
    flowMetrics.countRecordProcessed();
    // Pre-fold dedup (ADR 0007): skip a producer duplicate — the same Zeebe record re-appended at
    // a later Event Bridge offset arrives at-or-below its Zeebe partition's applied-position
    // watermark. The Event Bridge offset still advances for skipped records (the runtime marks
    // them processed regardless), so consumption progress is unaffected.
    final int zeebePartition = record.record().getPartitionId();
    final long zeebePosition = record.record().getPosition();
    final Long watermark = appliedWatermarks.get(zeebePartition);
    if (watermark != null && zeebePosition <= watermark) {
      metrics.duplicateSkipped();
      flowMetrics.countDedupSkipped();
      return;
    }
    topology.process(record);
    appliedWatermarks.put(zeebePartition, zeebePosition);
    // Eager shuffle publish (opt-in, no-op otherwise): any segment this fold sealed forwarded its
    // cells into the publisher synchronously above, so they can leave for the facts topic now —
    // non-blocking — instead of waiting for the commit barrier's publish burst.
    publisher.publishSealedEagerly();
  }

  @Override
  public void flush() {
    topology.flush();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    topology.advanceStreamTime(streamTimeMs);
    publisher.publishSealedEagerly();
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    topology.punctuateWallClock(wallClockMs);
    publisher.publishSealedEagerly();
  }

  @Override
  public boolean needsCheckpoint() {
    return topology.needsCheckpoint();
  }

  @Override
  public CommitCut freezeCut(final long offset) {
    // Silent-empty-cube check at the commit boundary (two long reads per cube, never per fact):
    // a cube whose filters matched nothing across this many folds warns once — see the processor.
    for (final CubeWiring wiring : wiringByStreamId.values()) {
      wiring.processor().warnIfSilent();
    }
    // Liveness: seal every segment the source has fully advanced past (the committed offset is the
    // watermark), so sparse cells — e.g. an incident meter that then goes quiet — reach the shuffle
    // even without a natural boundary crossing. The seal forwards SegmentCells into the shuffle
    // sink, so it must run before the publisher freeze below. Using the offset as the watermark is
    // sound because the pre-fold dedup in process() eliminates producer duplicates (ADR 0007); see
    // SegmentSealingAggregation#sealCompletedUpTo.
    for (final SegmentSealingAggregation<Fact, ?, ?> aggregation : sealingAggregations) {
      aggregation.sealCompletedUpTo(offset);
    }
    // Everything the barrier's folds produced is already staged — projected rows at process(),
    // sealed cells at the seal (SegmentSink.flush is a no-op) — so freezing the publisher and the
    // serving buffer converges the cut's produced output; the cut publishes it on the IO thread.
    final EnvelopePublisher publisher = this.publisher;
    publisher.freeze();
    // The watermark seal above closed every stream below the committed offset's segment, so their
    // chunk counters (just consumed by the freeze's encode) can never be consulted again — prune
    // them to keep the publisher's per-segment chunk map bounded.
    publisher.pruneChunkCountersBelow(segments.index(offset));
    final FreezableDatasetWriter servingWriter = this.servingWriter;
    // The barrier's write fence: this cut's rows carry (current ownership epoch, cut offset), so
    // the serving store rejects them if a newer owner has already written past us.
    servingWriter.freeze(new WriteVersion(epoch.current(), offset));
    // The pre-fold dedup watermarks are snapshotted AT THE BARRIER: the persisted watermarks must
    // describe exactly the folds in the frozen state. A snapshot taken at persist time would cover
    // positions folded after the freeze — folds the frozen cut does not contain — and a
    // crash-replay would skip them as producer duplicates: silent data loss.
    final Map<Integer, Long> frozenWatermarks = Map.copyOf(appliedWatermarks);
    final StateBackedProjectionState state = projectionState;
    state.freeze();
    final List<SegmentSealingAggregation<Fact, ?, ?>> aggregations = sealingAggregations;
    aggregations.forEach(SegmentSealingAggregation::freeze);

    return new CommitCut() {

      @Override
      public void publish() {
        // Produce-before-commit: the frozen sealed shuffle deltas and the frozen projected rows
        // become durable at their destinations before the offset advances; both re-apply
        // idempotently on a replay (segment/chunk dedup downstream, full-value upserts).
        publisher.publishFrozen();
        servingWriter.publishFrozen();
      }

      @Override
      public void persist() {
        // The frozen offset + the frozen dedup watermarks + the frozen topology state (base
        // projection + every open segment) as one atomic cut on this partition's provider. The
        // task's key/value flyweights are safe here: they are only ever touched on this commit
        // path, and cuts are single-flight per partition.
        provider.runInTransaction(
            () -> {
              offsetKey.wrapInt(partition);
              offsetValue.wrapLong(offset);
              offsets.put(offsetKey, offsetValue);
              // The pre-fold dedup watermarks join the same cut (one long per Zeebe partition), so
              // a crash-replay resumes from the committed offset with the matching watermark state.
              for (final Map.Entry<Integer, Long> watermark : frozenWatermarks.entrySet()) {
                appliedKey.wrapInt(watermark.getKey());
                appliedValue.wrapLong(watermark.getValue());
                appliedPositions.put(appliedKey, appliedValue);
              }
              state.persistFrozen();
              aggregations.forEach(SegmentSealingAggregation::persistFrozen);
            });
      }

      @Override
      public void complete(final boolean success) {
        publisher.completeFrozen(success);
        servingWriter.completeFrozen(success);
        state.completeFrozen(success);
        aggregations.forEach(aggregation -> aggregation.completeFrozen(success));
        // The live appliedWatermarks map stayed authoritative throughout; the frozen copy is
        // simply dropped either way — a failed cut's watermarks are re-captured (together with
        // any newer ones) by the next freeze.
        if (success) {
          maybeReload();
        }
      }
    };
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
      // Drain any rows staged since the last commit — idempotent upserts ahead of the offset cut
      // are safe (a replay re-writes them), and it keeps the serving view as fresh as before the
      // staging buffer existed, where in-flight rows were committed by the writer's close.
      servingWriter.flush();
      datasetStore.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close serving store for partition {}", partition, e);
    }
    try {
      provider.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close state provider for partition {}", partition, e);
    }
    // Deregister this task's meters so a re-opened partition task's registrations are not
    // silently ignored and this task becomes collectable (the gauges hold strong references).
    storeMetrics.close();
    meterDisposals.forEach(Runnable::run);
    meterDisposals.clear();
  }

  /** Registers a per-task meter deregistration to run when this task closes. */
  void disposeOnClose(final Runnable disposal) {
    meterDisposals.add(disposal);
  }
}
