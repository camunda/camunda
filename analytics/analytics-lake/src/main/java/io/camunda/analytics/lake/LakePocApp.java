/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake;

import io.camunda.analytics.lake.catalog.LakeCommitCoordinator;
import io.camunda.analytics.lake.metrics.CompiledEntityMetrics;
import io.camunda.analytics.lake.metrics.EntityMetrics;
import io.camunda.analytics.lake.metrics.MetricsRider;
import io.camunda.analytics.lake.metrics.PollFedRider;
import io.camunda.analytics.lake.objects.CompiledObjectTypes;
import io.camunda.analytics.lake.objects.ObjectTypes;
import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import io.camunda.analytics.lake.sink.batch.Interner;
import io.camunda.analytics.lake.sink.batch.SegmentFactory;
import io.camunda.analytics.lake.sink.batch.SegmentRowAppender;
import io.camunda.analytics.lake.sink.batch.SegmentSorter;
import io.camunda.analytics.lake.sink.encode.IcebergParquetEncoderFactory;
import io.camunda.analytics.lake.sink.encode.LocalFileSink;
import io.camunda.analytics.lake.sink.pipeline.CoordinatedDescriptorSink;
import io.camunda.analytics.lake.sink.pipeline.SinkConfig;
import io.camunda.analytics.lake.sink.pipeline.SinkPipeline;
import io.camunda.analytics.lake.state.RocksDbTranslatorState;
import io.camunda.analytics.lake.state.StateSnapshotDumper;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.translate.LakeTranslator;
import io.camunda.analytics.lake.translate.RawTableSchemas;
import io.camunda.analytics.lake.ui.LakeUiServer;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import io.camunda.analytics.lake.write.LakeCompactor;
import io.camunda.analytics.lake.write.LocalFileIO;
import io.camunda.analytics.lake.write.OpenInstancesGaugeSampler;
import io.camunda.analytics.lake.write.OpenInstancesGaugeWriter;
import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the lake PoC: subscribes to Zeebe records from the Event Bridge as its own
 * consumer group, folds them through {@link LakeTranslator} into the L0 sink's {@link
 * SinkPipeline}s (one pair — {@code instances}, {@code activities} — per owned partition, per
 * {@link SinkConfig}'s own "one instance per (source partition x Iceberg table)" contract), and
 * lets each pipeline's own flush thread own the flush/file-boundary policy via {@link
 * SinkPipeline#onPollTick}.
 *
 * <h2>Offset authority</h2>
 *
 * <p>This app does <em>not</em> rely on the Event Bridge consumer-group protocol's own server-side
 * committed offset ({@link Consumer#commitOffset}) for correctness — the lake's snapshot summary is
 * the sole durable offset authority (see {@link IcebergLakeWriter#committedOffset(int)}, which
 * reads exactly the same {@code lake.offset.p*} property {@link CoordinatedDescriptorSink} stamps).
 * At startup, and on every partition (re)assignment, this app explicitly {@link Consumer#seek}s to
 * {@code committedOffset(int) + 1}. The coordinator is used only for group membership and partition
 * assignment, never for resume position. As a second, cheap line of defense, every polled record is
 * also checked against a locally cached committed offset before being processed — belt and braces,
 * in case a seek is ever missed (e.g. a narrow race on a rebalance).
 *
 * <h2>Raw-table ingest: the L0 sink, not the old buffered writer</h2>
 *
 * <p>{@link IcebergLakeWriter} is still constructed here — its table creation (schemas + field ids
 * + name-mapping), {@link IcebergLakeWriter#committedOffset(int)}, and the DuckDB connection {@link
 * LakeCompactor} still drives for compaction work are all still needed — but its buffered {@code
 * append}/{@code flush} raw-ingest path is never called from this class anymore. Instead, each
 * owned partition gets its own {@link SinkPipeline} pair, fed directly by {@link LakeTranslator}
 * through a {@link RowAppender}, flushed on its own schedule by its own flush thread, and committed
 * to the {@code instances}/{@code activities} {@link Table}s (plus the rider-derived partials
 * tables) via one shared {@link CoordinatedDescriptorSink} — every descriptor becomes one atomic
 * catalog transaction through {@link LakeCommitCoordinator}, whose database row locks serialize
 * concurrent committers (multiple partitions' flush threads, and {@link LakeCompactor}'s own
 * poll-thread commits).
 */
public final class LakePocApp {

  private static final Logger LOG = LoggerFactory.getLogger(LakePocApp.class);

  private static final int POLL_SIZE = 500;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(10);

  /** L0 sink ring geometry — see {@code SinkConfig}'s own javadoc for what each knob decides. */
  private static final int SEGMENT_ROWS = 16384;

  private static final int RING_SEGMENTS = 8;

  /** Approximate target file size (see {@code SinkConfig#fileTargetBytes}'s own javadoc). */
  private static final long FILE_TARGET_BYTES = 128L * 1024 * 1024;

  /**
   * Bytes budgeted per row for the {@code instances} table's {@code vars_json} BINARY column (see
   * {@code SegmentFactory#createSegments}'s {@code binaryAvgBytesPerRow} parameter) — a PoC-tuned
   * guess, not a measured production figure; a process whose finished instances carry unusually
   * large root-scope variable payloads could exceed it and fail that pipeline (see {@code
   * HeapBinaryColumn}'s arena-exhaustion javadoc). Raise this if that happens in practice.
   */
  private static final int VARS_JSON_AVG_BYTES_PER_ROW = 512;

  /**
   * Ring geometry shared by every small, dedicated <b>dictionary-kind</b> pipeline — {@code
   * variants} (see {@code RawTableSchemas#variants}), and the OCPM object fabric's {@code objects}/
   * {@code instance_links}/{@code object_relations} (see {@code RawTableSchemas#objects}/{@code
   * #instanceLinks}/{@code #objectRelations}) — deliberately much smaller than {@link
   * #SEGMENT_ROWS}/{@link #RING_SEGMENTS} above: rows are one per distinct key ever seen, bounded
   * by the number of distinct keys rather than by instance volume, and {@code LakeTranslator}'s own
   * per-translator seen-caches already suppress most repeat writes before a row is even attempted
   * (see their javadocs). {@link SinkConfig#ringSegments()} still requires at least 2 (one FILLING
   * slot, one SEALED slot for backpressure to have room to bite). One shared constant set (rather
   * than one per table) since every dictionary-kind pipeline has the same tiny-row, low-volume
   * shape — see this class's own {@code buildPartitionPipelines}/{@code buildSinkWiring} for where
   * each pipeline is built.
   */
  private static final int DICTIONARY_SEGMENT_ROWS = 1024;

  /** See {@link #DICTIONARY_SEGMENT_ROWS}. */
  private static final int DICTIONARY_RING_SEGMENTS = 2;

  /**
   * Bytes budgeted per row for a dictionary-kind table's own BINARY column(s), if any (see {@code
   * SegmentFactory#createSegments}'s {@code binaryAvgBytesPerRow} parameter) — only {@code
   * variants} has any (its {@code elements}/{@code flows} columns); the OCPM object-fabric
   * dictionary tables have none, so this value is simply unused for their pipelines (harmless —
   * {@code newPipeline} only applies it to columns actually typed {@code BINARY}). A PoC-tuned
   * guess sized for a moderately large process's joined, newline-separated id list; see {@link
   * #VARS_JSON_AVG_BYTES_PER_ROW}'s own javadoc for the same caveat.
   */
  private static final int DICTIONARY_BINARY_AVG_BYTES_PER_ROW = 2048;

  /**
   * Bytes budgeted per row for the {@code process_definitions} table's {@code bpmn_xml} column —
   * shares {@link #DICTIONARY_SEGMENT_ROWS}/{@link #DICTIONARY_RING_SEGMENTS}' ring geometry (a
   * dictionary-kind table like every other one {@link #DICTIONARY_BINARY_AVG_BYTES_PER_ROW}
   * budgets), but needs its own, much larger byte budget: a full BPMN 2.0 document, not a short
   * joined id list. {@code newPipeline}'s {@code binaryAvgBytesPerRowValue} parameter is applied
   * per-pipeline (see its own javadoc), so this table's ring can be sized independently without
   * inflating every other dictionary pipeline's arena. Real-world BPMN documents run from a few KB
   * to tens of KB (see {@code LakeTranslator#MAX_BPMN_XML_BYTES}'s own javadoc for the hard 1 MiB
   * cap on any single row); this budget is generous headroom over that typical range, not the cap
   * itself — the arena is a per-segment total shared across up to {@link #DICTIONARY_SEGMENT_ROWS}
   * rows (see {@code HeapBinaryColumn}'s own javadoc), so a burst of unusually many, unusually
   * large distinct definitions folded before a single flush could still exhaust it and throw; raise
   * this further if that happens in practice.
   */
  private static final int PROCESS_DEFINITIONS_BINARY_AVG_BYTES_PER_ROW = 65536;

  private static final long PARK_NANOS_ON_BACKPRESSURE = 1_000_000L; // 1ms

  private LakePocApp() {}

  public static void main(final String[] args) {
    final Handle handle = start(configFromSystemProperties());
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutdown requested; draining poll loop");
                  handle.close();
                  LOG.info("Lake PoC translator stopped");
                },
                "lake-poc-shutdown"));
    handle.awaitPollLoop();
  }

  /**
   * The embeddable entry point: everything {@link #main} does except process-level concerns
   * (system-property parsing, the JVM shutdown hook, blocking the caller). The returned {@link
   * Handle} owns every resource and the poll thread; callers hosting ingest inside a larger process
   * (e.g. the serving application) call this at startup and {@link Handle#close()} at shutdown —
   * the seam the one-backend consolidation builds on.
   */
  public static Handle start(final LakeConfig config) {
    LOG.info(
        "Starting lake PoC translator: gateway={} topic={} group={} warehouse={} state={} "
            + "flushIntervalMs={} stateDumpIntervalMs={} compactIntervalMs={} uiPort={} "
            + "gaugeFlushIntervalMs={}",
        config.contactPoint(),
        config.topic(),
        config.consumerGroup(),
        config.warehouseDir(),
        config.stateDir(),
        config.flushIntervalMs(),
        config.stateDumpIntervalMs(),
        config.compactIntervalMs(),
        config.uiPort(),
        config.gaugeFlushIntervalMs());

    final EventBridgeClient client = EventBridgeClient.create(config.contactPoint());
    final IcebergLakeWriter icebergWriter = new IcebergLakeWriter(config);
    final TranslatorState state = new RocksDbTranslatorState(config.stateDir());
    final StateSnapshotDumper stateSnapshotDumper =
        new StateSnapshotDumper(state, config.stateDir().resolve("_snapshot"));
    final LakeCompactor compactor = new LakeCompactor(icebergWriter);
    // Not registered with LakeCompactor's per-table pass -- see this app's own start()/Handle
    // javadoc note near its construction below for why open_instances_gauge is deliberately left
    // out of compaction for now.
    final OpenInstancesGaugeWriter gaugeWriter = new OpenInstancesGaugeWriter(icebergWriter);
    final long appStartMs = System.currentTimeMillis();
    final OpenInstancesGaugeSampler gaugeSampler =
        new OpenInstancesGaugeSampler(gaugeWriter, config.gaugeFlushIntervalMs(), appStartMs);
    // Emit a sample immediately at startup -- a standalone scan (see
    // StateSnapshotDumper#countOpenInstancesByProcess's own javadoc), so a fresh stack has a first
    // gauge data point instead of waiting for the first periodic stateDumpIntervalMs tick.
    gaugeSampler.tick(appStartMs, stateSnapshotDumper.countOpenInstancesByProcess());

    final SinkWiring wiring = buildSinkWiring(config, icebergWriter);

    // The UI server owns its own embedded DuckDB connection (read-only usage over read_parquet
    // views) -- entirely separate from the writer's, so a demo query browsing the lake can never
    // contend with or block the translator's own flush path. uiPort == 0 disables it.
    final LakeUiServer uiServer =
        config.uiPort() > 0
            ? new LakeUiServer(
                config.uiPort(), config.warehouseDir(), config.stateDir(), config.bpmnDir())
            : null;
    if (uiServer != null) {
      uiServer.start();
      LOG.info("Lake UI: http://localhost:{}", config.uiPort());
    }

    final String consumerId = "lake-poc-" + ProcessHandle.current().pid();
    final ZeebeRecordConsumer consumer =
        ZeebeRecordConsumer.subscribe(
                client, config.consumerGroup(), consumerId, List.of(config.topic()))
            .join();
    final Consumer rawConsumer = consumer.unwrap();

    // Cache of the writer's own committed offset per partition, updated only on flush, so the
    // per-record replay guard in the poll loop is a cheap map lookup rather than a call into the
    // writer (which does an Iceberg table refresh + snapshot summary read).
    final Map<Integer, Long> cachedCommittedOffset = new ConcurrentHashMap<>();
    // One (instances, activities) SinkPipeline pair per owned partition -- built lazily on
    // assignment (see seekTo), read from the poll loop thread only.
    final Map<Integer, PartitionPipelines> partitionPipelines = new ConcurrentHashMap<>();

    // Register the listener BEFORE the first heartbeat so the initial assignment is observed
    // through it too, per RebalanceListener's own javadoc contract ("set it before joinGroup/first
    // heartbeat"). This covers reassignment for the lifetime of the run (scale-out, this member
    // losing and regaining a partition, etc). Partition revocation cleanup (closing a pipeline
    // whose
    // partition was taken away) is not handled, mirroring this app's pre-existing scope (the old
    // buffered-writer path never handled it either).
    rawConsumer.rebalanceListener(
        new RebalanceListener() {
          @Override
          public void onPartitionsAssigned(final Collection<TopicPartition> assigned) {
            seekTo(
                consumer,
                icebergWriter,
                cachedCommittedOffset,
                assigned,
                state,
                wiring,
                partitionPipelines);
          }
        });

    // subscribe()/joinGroup() only registers group membership -- the coordinator only hands out
    // the first partition assignment on a heartbeat response, which would otherwise not arrive
    // until the ~3s scheduled heartbeat interval elapses. EventBridgeClient's own usage example
    // drives this synchronously instead: send one heartbeat immediately and wait for it.
    rawConsumer.sendHeartbeat().join();

    // Belt and braces alongside the listener above: read the assignment directly too and seek it,
    // in case this process's very first assignment landed before the listener registration above
    // had (for whatever reason) taken effect.
    seekTo(
        consumer,
        icebergWriter,
        cachedCommittedOffset,
        rawConsumer.getOwnedPartitions(),
        state,
        wiring,
        partitionPipelines);
    LOG.info(
        "Startup summary: owns {} partition(s): {}",
        rawConsumer.getOwnedPartitions().size(),
        rawConsumer.getOwnedPartitions());

    final AtomicBoolean running = new AtomicBoolean(true);
    final Thread pollThread =
        new Thread(
            () ->
                runLoop(
                    consumer,
                    icebergWriter,
                    cachedCommittedOffset,
                    partitionPipelines,
                    running,
                    stateSnapshotDumper,
                    compactor,
                    wiring.coordinator(),
                    state,
                    config.stateDumpIntervalMs(),
                    config.compactIntervalMs(),
                    config.objectTombstoneRetentionMs(),
                    gaugeSampler),
            "lake-poll-loop");
    pollThread.start();
    return new Handle(
        client,
        icebergWriter,
        state,
        uiServer,
        consumer,
        partitionPipelines,
        running,
        pollThread,
        gaugeSampler);
  }

  /**
   * A running ingest's lifecycle handle, returned by {@link #start}: owns every resource and the
   * poll thread. {@link #close()} drains and releases everything in dependency order — safe to call
   * from a JVM shutdown hook ({@link #main} does) or a host container's lifecycle callback (the
   * serving application's ingest bean does). Idempotent: a second close is a no-op.
   */
  public static final class Handle implements AutoCloseable {

    private final EventBridgeClient client;
    private final IcebergLakeWriter icebergWriter;
    private final TranslatorState state;
    private final LakeUiServer uiServer;
    private final ZeebeRecordConsumer consumer;
    private final Map<Integer, PartitionPipelines> partitionPipelines;
    private final AtomicBoolean running;
    private final Thread pollThread;
    private final OpenInstancesGaugeSampler gaugeSampler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Handle(
        final EventBridgeClient client,
        final IcebergLakeWriter icebergWriter,
        final TranslatorState state,
        final LakeUiServer uiServer,
        final ZeebeRecordConsumer consumer,
        final Map<Integer, PartitionPipelines> partitionPipelines,
        final AtomicBoolean running,
        final Thread pollThread,
        final OpenInstancesGaugeSampler gaugeSampler) {
      this.client = client;
      this.icebergWriter = icebergWriter;
      this.state = state;
      this.uiServer = uiServer;
      this.consumer = consumer;
      this.partitionPipelines = partitionPipelines;
      this.running = running;
      this.pollThread = pollThread;
      this.gaugeSampler = gaugeSampler;
    }

    /** Blocks until the poll loop exits (normally only on {@link #close()}). */
    public void awaitPollLoop() {
      try {
        pollThread.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      running.set(false);
      try {
        // Joining the poll thread establishes happens-before for everything it wrote before
        // exiting its loop, so no extra synchronization is needed to safely touch
        // partitionPipelines/state/writer from here afterward.
        pollThread.join(SHUTDOWN_DRAIN_TIMEOUT.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      // Draining each pipeline seals whatever the filling segment holds, waits for the flush
      // thread to finalize files and commit a last descriptor, then stops -- see
      // SinkPipeline#close's own javadoc. Must happen before the writer (and its catalog/DuckDB
      // connection) closes.
      for (final PartitionPipelines pipelines : partitionPipelines.values()) {
        pipelines.close();
      }
      if (uiServer != null) {
        uiServer.close();
      }
      // Flushes any buffered-but-not-yet-committed gauge samples -- must happen before the writer
      // (and its shared DuckDB connection/catalog) closes, since OpenInstancesGaugeWriter reuses
      // that same connection and Table handle (see IcebergLakeWriter#openInstancesGaugeTable's own
      // javadoc). Safe to run after the poll thread has already stopped calling tick(): the poll
      // loop is the sampler's only other caller (see runLoop), and it has already exited by now.
      gaugeSampler.close();
      state.close();
      icebergWriter.close();
      consumer.close();
      client.close();
    }
  }

  /** Builds the per-table plumbing shared by every partition's {@link SinkPipeline} pair. */
  private static SinkWiring buildSinkWiring(
      final LakeConfig config, final IcebergLakeWriter writer) {
    final Table instancesTable = writer.instancesTable();
    final Table activitiesTable = writer.activitiesTable();
    final Table variantsTable = writer.variantsTable();
    final Table objectsTable = writer.objectsTable();
    final Table instanceLinksTable = writer.instanceLinksTable();
    final Table objectRelationsTable = writer.objectRelationsTable();
    final Table objectLifecycleTable = writer.objectLifecycleTable();
    final Table processDefinitionsTable = writer.processDefinitionsTable();
    final TableSchema instancesSchema = RawTableSchemas.instances(instancesTable.schema());
    final TableSchema activitiesSchema = RawTableSchemas.activities(activitiesTable.schema());
    final TableSchema variantsSchema = RawTableSchemas.variants(variantsTable.schema());
    final TableSchema objectsSchema = RawTableSchemas.objects(objectsTable.schema());
    final TableSchema instanceLinksSchema =
        RawTableSchemas.instanceLinks(instanceLinksTable.schema());
    final TableSchema objectRelationsSchema =
        RawTableSchemas.objectRelations(objectRelationsTable.schema());
    final TableSchema objectLifecycleSchema =
        RawTableSchemas.objectLifecycle(objectLifecycleTable.schema());
    final TableSchema processDefinitionsSchema =
        RawTableSchemas.processDefinitions(processDefinitionsTable.schema());

    // OCPM object-type declarations for this demo -- configuration-as-code, mirroring the
    // EntityMetrics declarations below. See ObjectTypes' own javadoc and the "customer"/"dispute"
    // declarations' own comment for what the bank-dispute-handling load driver actually carries.
    final CompiledObjectTypes objectTypes = demoObjectTypes();

    // The metrics declarations -- dims, window, measures; everything downstream (partials
    // schemas, tables, rider plans, merge/finalize SQL, fingerprint) derives from them.
    final CompiledEntityMetrics activityMetrics =
        EntityMetrics.declare("activities", activitiesSchema)
            .dims("process_id", "element_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    final CompiledEntityMetrics instanceMetrics =
        EntityMetrics.declare("instances", instancesSchema)
            .dims("process_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    // Per-variant metrics: a second declaration over the SAME raw "instances" schema (nothing
    // about a declaration requires exclusivity over its raw schema) -- dims (process_id,
    // variant_hash) instead of (process_id), everything else identical. Rides the instances
    // pipeline as a second MetricsRider alongside instanceMetrics's own (see
    // #buildPartitionPipelines) -- FlushLoop's rider list is a List<SealRider>, so two riders on
    // one pipeline is already supported machinery, not a workaround.
    final CompiledEntityMetrics instanceVariantMetrics =
        EntityMetrics.declare("instance_variants", instancesSchema)
            .dims("process_id", "variant_hash")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    // Record-fed: dims(process_id, version, flow_id, source_element_id, target_element_id)
    // resolved by LakeTranslator from a sequence-flow-taken record plus its definition's own
    // deployed BPMN (see LakeTranslator#onProcess/#resolveFlowEndpoints); a purely logical row
    // shape backing no real raw table (see EntityMetrics#declare's own javadoc) and PollFedRider's
    // own class javadoc). count()-only: these events carry nothing worth measuring, only counting.
    final CompiledEntityMetrics flowMetrics =
        EntityMetrics.declare("flows", flowsVirtualSchema())
            .dims("process_id", "version", "flow_id", "source_element_id", "target_element_id")
            .window(Duration.ofMinutes(1), "taken_at")
            .count()
            .build();
    // Record-fed, same idea, for process-instance-started events (see LakeTranslator's own
    // ELEMENT_ACTIVATED/root handling).
    final CompiledEntityMetrics instanceStartMetrics =
        EntityMetrics.declare("instance_starts", instanceStartsVirtualSchema())
            .dims("process_id", "version")
            .window(Duration.ofMinutes(1), "started_at")
            .count()
            .build();
    // Row-fed, same MetricsRider machinery as instanceMetrics/activityMetrics above -- rides the
    // instances raw table's own completion rows, keyed by the instance's OWN start slot (not its
    // completion slot) so a cohort's started_cnt (live, from instanceStartMetrics above) and this
    // entity's completed cnt/histogram (at completion) together give survival/cohort analysis with
    // correct censoring: still-running per cohort = started_cnt - instance_cohorts_metrics.cnt.
    final CompiledEntityMetrics instanceCohortMetrics =
        EntityMetrics.declare("instance_cohorts", instancesSchema)
            .dims("process_id")
            .window(Duration.ofHours(1), "started_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    // Record-fed, same idea as flowMetrics/instanceStartMetrics: dims(process_id, var_name), one
    // row per (process id, variable name, minute) -- minutes, not hours, matching every other
    // declaration in this app (all 1m): minute partials merge into hours forever via the generated
    // coarsening SQL, hours can never be split back down, and a drift-correlation read (this
    // variable's own series against the minute-slotted duration p95 series above) needs no
    // resolution mismatch to line up. Sparse per-minute counts are not a statistical concern either
    // way: readers always merge ranges, and 60 merged minute-increments equal one stored hour.
    // count() is the per-group instance total; the five named counters are the type-mix (see
    // LakeTranslator#foldVariableProfiles's own PROFILE_COUNTER_* ordering, which this declaration
    // order must match); the "value" measure folds only when the variable's final value was a
    // number (LakeTranslator's own null-measure-skip path covers every other type).
    final CompiledEntityMetrics profileMetrics =
        EntityMetrics.declare("variable_profiles", variableProfilesVirtualSchema())
            .dims("process_id", "var_name")
            .window(Duration.ofMinutes(1), "completed_at")
            .count()
            .counter("type_number_cnt")
            .counter("type_string_cnt")
            .counter("type_boolean_cnt")
            .counter("type_null_cnt")
            .counter("type_object_or_array_cnt")
            .measure("value", Algebras.doubleScalarStats(), Algebras.signedDoubleExpHistogram(3))
            .build();
    // Object lifecycle capture: record-fed, same idea as flowMetrics/instanceStartMetrics -- an
    // object's first-ever sighting (LakeTranslator#foldObjectLifecycleSighting) produces no raw
    // row of its own, only a birth counter. count()-only: a birth carries nothing worth measuring,
    // only counting.
    final CompiledEntityMetrics objectsBornMetrics =
        EntityMetrics.declare("objects_born", objectsBornVirtualSchema())
            .dims("object_type")
            .window(Duration.ofMinutes(1), "birth_ts")
            .count()
            .build();
    // Row-fed, same MetricsRider machinery as instanceMetrics/instanceCohortMetrics above -- rides
    // the object_lifecycle table's own closing rows, keyed by birth_ts (not closed_at, mirroring
    // instanceCohortMetrics's own started_at-keyed cohort choice): closed counts per cohort = this
    // entity's own cnt; survival = objects_born's born count minus this entity's cnt, at read time.
    final CompiledEntityMetrics objectCohortMetrics =
        EntityMetrics.declare("object_cohorts", objectLifecycleSchema)
            .dims("object_type")
            .window(Duration.ofMinutes(1), "birth_ts")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // Mirrors the "<table.location()>/data/<fileName>" convention IcebergLakeWriter's own legacy
    // path uses -- see LocalFileSink's javadoc.
    final LocalFileSink fileSink = new LocalFileSink(config.warehouseDir().resolve("lake"));
    final IcebergParquetEncoderFactory instancesEncoderFactory =
        new IcebergParquetEncoderFactory(
            instancesTable.schema(), fileSink, SEGMENT_ROWS, Set.of("key"));
    final IcebergParquetEncoderFactory activitiesEncoderFactory =
        new IcebergParquetEncoderFactory(
            activitiesTable.schema(),
            fileSink,
            SEGMENT_ROWS,
            Set.of("instance_key", "element_key"));
    final IcebergParquetEncoderFactory variantsEncoderFactory =
        new IcebergParquetEncoderFactory(
            variantsTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());
    final IcebergParquetEncoderFactory objectsEncoderFactory =
        new IcebergParquetEncoderFactory(
            objectsTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());
    final IcebergParquetEncoderFactory instanceLinksEncoderFactory =
        new IcebergParquetEncoderFactory(
            instanceLinksTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());
    final IcebergParquetEncoderFactory objectRelationsEncoderFactory =
        new IcebergParquetEncoderFactory(
            objectRelationsTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());
    // Small, dedicated ring like every other object-fabric table -- see DICTIONARY_SEGMENT_ROWS'
    // own javadoc; object_lifecycle rows are one per object closing, bounded by distinct-object
    // count, not instance volume.
    final IcebergParquetEncoderFactory objectLifecycleEncoderFactory =
        new IcebergParquetEncoderFactory(
            objectLifecycleTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());
    // Dictionary-kind like every table above, but its own byte budget -- see
    // PROCESS_DEFINITIONS_BINARY_AVG_BYTES_PER_ROW's own javadoc for why bpmn_xml needs one.
    final IcebergParquetEncoderFactory processDefinitionsEncoderFactory =
        new IcebergParquetEncoderFactory(
            processDefinitionsTable.schema(), fileSink, DICTIONARY_SEGMENT_ROWS, Set.of());

    // Every pipeline commits through the one coordinator: raw-only descriptors are a batch of one,
    // rider-carrying descriptors fan out atomically -- a single commit path either way.
    final LakeCommitCoordinator coordinator = new LakeCommitCoordinator(writer.jdbcUrl(), "lake");
    final Map<String, Table> tablesByName = new HashMap<>();
    tablesByName.put("instances", instancesTable);
    tablesByName.put("activities", activitiesTable);
    tablesByName.put("variants", variantsTable);
    tablesByName.put("objects", objectsTable);
    tablesByName.put("instance_links", instanceLinksTable);
    tablesByName.put("object_relations", objectRelationsTable);
    tablesByName.put("object_lifecycle", objectLifecycleTable);
    tablesByName.put("process_definitions", processDefinitionsTable);
    // The rider's one factory dispatches by generated-schema table name -- each partials table has
    // its own iceberg schema and thus its own underlying encoder factory.
    final Map<String, BatchEncoder.Factory> partialsFactories = new HashMap<>();
    registerPartials(writer, activityMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, instanceMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, instanceVariantMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, flowMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, instanceStartMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, instanceCohortMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, profileMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, objectsBornMetrics, fileSink, tablesByName, partialsFactories);
    registerPartials(writer, objectCohortMetrics, fileSink, tablesByName, partialsFactories);
    final BatchEncoder.Factory partialsEncoderFactory =
        (schema, epochDay) -> partialsFactories.get(schema.table()).newFile(schema, epochDay);

    final CoordinatedDescriptorSink commitSink =
        new CoordinatedDescriptorSink(coordinator, Namespace.of("lake"), tablesByName::get);

    return new SinkWiring(
        instancesSchema,
        activitiesSchema,
        variantsSchema,
        objectsSchema,
        instanceLinksSchema,
        objectRelationsSchema,
        objectLifecycleSchema,
        processDefinitionsSchema,
        instancesEncoderFactory,
        activitiesEncoderFactory,
        variantsEncoderFactory,
        objectsEncoderFactory,
        instanceLinksEncoderFactory,
        objectRelationsEncoderFactory,
        objectLifecycleEncoderFactory,
        processDefinitionsEncoderFactory,
        commitSink,
        commitSink,
        commitSink,
        commitSink,
        commitSink,
        commitSink,
        commitSink,
        commitSink,
        instanceMetrics,
        activityMetrics,
        instanceVariantMetrics,
        flowMetrics,
        instanceStartMetrics,
        instanceCohortMetrics,
        profileMetrics,
        objectsBornMetrics,
        objectCohortMetrics,
        partialsEncoderFactory,
        coordinator,
        config.flushIntervalMs(),
        new SimpleMeterRegistry(),
        objectTypes);
  }

  /**
   * The demo OCPM object-type declaration for this app's driver load ({@code
   * bankCustomerComplaintDisputeHandling.bpmn}, run via {@code analytics/run-realistic-load.sh}):
   *
   * <ul>
   *   <li><b>{@code customer}</b>, identified by the root-scope {@code customerId} variable — a
   *       plain numeric field the starter's own payload ({@code realisticPayload.json}) sets on
   *       every instance at creation, so it is always root-scoped.
   *   <li><b>{@code dispute}</b>, identified by the {@code correlationKey} variable — this process
   *       never sets it at root: it is computed by two separate embedded-subprocess start-event
   *       output mappings (the "Document Request Process" subprocess: {@code disputeId + "-" +
   *       customerId}; the nested "Vendor fraud claim validation" multi-instance subprocess, once
   *       per {@code disputePosition}: {@code customerId + "-" + disputePosition.name}), so every
   *       sighting of it is non-root. Paired with {@code customer}'s always-root sighting, one
   *       instance genuinely exercises the v1 relations rule (root {@code customer} ⊇ non-root
   *       {@code dispute}) end to end against real driver data, with no synthetic setup. {@code
   *       dispute} also {@link ObjectTypes.Builder#closes closes} on {@code bankDisputeHandling}'s
   *       own completion — the driver BPMN's own {@code bpmnProcessId} (see {@code
   *       bankCustomerComplaintDisputeHandling.bpmn}): every embedded subprocess this process
   *       contains shares its root process instance, so the top-level process's own completion is
   *       exactly the moment a dispute's handling is done, one way or another (see {@code
   *       LakeTranslator}'s "Object lifecycle capture" javadoc section for the full scheme this
   *       exercises against real driver data).
   * </ul>
   *
   * <p>{@code customer} deliberately declares NO closing rule — the default-open case: every
   * customer sighted by this driver simply never emits a lifecycle fact, demonstrating that a type
   * with nothing declared is left alone rather than defaulting to some inferred closing behavior.
   *
   * <p>The correlation-key identifier source ({@code IdentifierSource.CorrelationKeyIdentifier},
   * sighting sources 2/3 — message correlation) is deliberately NOT exercised by this demo
   * declaration: nothing here declares it, so those two sighting sources are simply no-ops against
   * this particular driver (see {@code CompiledObjectTypes#correlationKeyIdentifiedType}'s own
   * javadoc) — covered instead by {@code LakeTranslator}'s own unit tests with a synthetic
   * declaration.
   */
  private static CompiledObjectTypes demoObjectTypes() {
    return CompiledObjectTypes.of(
        ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build(),
        ObjectTypes.declare("dispute")
            .identifiedBy(ObjectTypes.variable("correlationKey"))
            .closes(ObjectTypes.onProcessCompletion("bankDisputeHandling"))
            .build(),
        // The order-to-cash OCPM showcase's object model (see
        // event-bridge/event-bridge-examples/docs/ocpm-showcase.md for the planted ground truth
        // these two are asserted against). Both close when the top-level order-intake instance
        // completes: the intake instance is the root of the whole call-activity chain
        // (intake -> fulfillment -> invoicing), so its completion is exactly the moment the order
        // — and every item registered under it — is done. `item` demonstrates the non-root
        // sighting case: itemId only ever appears in a multi-instance body's per-iteration scope,
        // never at a process root.
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .closes(ObjectTypes.onProcessCompletion("order-intake"))
            .build(),
        ObjectTypes.declare("item")
            .identifiedBy(ObjectTypes.variable("itemId"))
            .closes(ObjectTypes.onProcessCompletion("order-intake"))
            .build());
  }

  /**
   * Creates (or loads) {@code compiled}'s {@code _metrics} table, and its {@code _hist} table too —
   * but only when {@link CompiledEntityMetrics#hasHistogram()} says there is one — registering each
   * under its own generated table name in both maps every partition's riders and the shared commit
   * sink share.
   */
  private static void registerPartials(
      final IcebergLakeWriter writer,
      final CompiledEntityMetrics compiled,
      final LocalFileSink fileSink,
      final Map<String, Table> tablesByName,
      final Map<String, BatchEncoder.Factory> partialsFactories) {
    final Table metricsTable =
        writer.partialsTableOrCreate(compiled.metricsSchema(), compiled.fingerprint());
    tablesByName.put(compiled.metricsSchema().table(), metricsTable);
    partialsFactories.put(
        compiled.metricsSchema().table(),
        new IcebergParquetEncoderFactory(metricsTable.schema(), fileSink, SEGMENT_ROWS, Set.of()));
    if (compiled.hasHistogram()) {
      final Table histTable =
          writer.partialsTableOrCreate(compiled.histSchema(), compiled.fingerprint());
      tablesByName.put(compiled.histSchema().table(), histTable);
      partialsFactories.put(
          compiled.histSchema().table(),
          new IcebergParquetEncoderFactory(histTable.schema(), fileSink, SEGMENT_ROWS, Set.of()));
    }
  }

  /**
   * A purely logical row shape (see {@code io.camunda.analytics.lake.metrics.EntityMetrics#declare}
   * and {@code io.camunda.analytics.lake.metrics.PollFedRider}'s own class javadocs) for one
   * qualifying {@code SEQUENCE_FLOW_TAKEN} record — no {@link SinkPipeline} ever materializes rows
   * of it. Field ids/sort order are meaningless here (never encoded, never sorted) and are set to
   * harmless placeholders.
   */
  private static TableSchema flowsVirtualSchema() {
    return new TableSchema(
        "flows",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column("version", ColumnType.INT, 2, false, -1, false),
            new TableSchema.Column("flow_id", ColumnType.STRING_DICT, 3, false, -1, false),
            new TableSchema.Column("source_element_id", ColumnType.STRING_DICT, 4, true, -1, false),
            new TableSchema.Column("target_element_id", ColumnType.STRING_DICT, 5, true, -1, false),
            new TableSchema.Column(
                "taken_at",
                ColumnType.LONG,
                6,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ)));
  }

  /** Same idea as {@link #flowsVirtualSchema()}, for one process-instance-started record. */
  private static TableSchema instanceStartsVirtualSchema() {
    return new TableSchema(
        "instance_starts",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column("version", ColumnType.INT, 2, false, -1, false),
            new TableSchema.Column(
                "started_at",
                ColumnType.LONG,
                3,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ)));
  }

  /**
   * Same idea as {@link #flowsVirtualSchema()}, for one object's first-ever sighting (see {@code
   * LakeTranslator#foldObjectLifecycleSighting}'s own javadoc).
   */
  private static TableSchema objectsBornVirtualSchema() {
    return new TableSchema(
        "objects_born",
        List.of(
            new TableSchema.Column("object_type", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column(
                "birth_ts",
                ColumnType.LONG,
                2,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ)));
  }

  /**
   * Same idea as {@link #flowsVirtualSchema()}, for one completed instance's own final root
   * variable — see {@code LakeTranslator#foldVariableProfiles}'s own javadoc for the fold. {@code
   * value} is the only {@code DOUBLE} column any virtual schema in this app declares: nullable,
   * since most folded records (non-numeric variable values) never stage it.
   */
  private static TableSchema variableProfilesVirtualSchema() {
    return new TableSchema(
        "variable_profiles",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column("var_name", ColumnType.STRING_DICT, 2, false, -1, false),
            new TableSchema.Column(
                "completed_at",
                ColumnType.LONG,
                3,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ),
            new TableSchema.Column("value", ColumnType.DOUBLE, 4, true, -1, false)));
  }

  private static void runLoop(
      final ZeebeRecordConsumer consumer,
      final IcebergLakeWriter icebergWriter,
      final Map<Integer, Long> cachedCommittedOffset,
      final Map<Integer, PartitionPipelines> partitionPipelines,
      final AtomicBoolean running,
      final StateSnapshotDumper stateSnapshotDumper,
      final LakeCompactor compactor,
      final LakeCommitCoordinator coordinator,
      final TranslatorState state,
      final long stateDumpIntervalMs,
      final long compactIntervalMs,
      final long objectTombstoneRetentionMs,
      final OpenInstancesGaugeSampler gaugeSampler) {
    // Single-threaded: only this loop mutates these, so plain HashMaps (not the ConcurrentHashMaps
    // used for cachedCommittedOffset/partitionPipelines, which the rebalance-listener thread also
    // touches) suffice.
    final Map<Integer, Long> lastAppliedOffset = new HashMap<>();
    final Map<Integer, Long> lastAppliedTimestampMs = new HashMap<>();
    long lastStateDumpAtMs = System.currentTimeMillis();
    long lastCompactAtMs = System.currentTimeMillis();
    while (running.get()) {
      final List<ZeebeRecord> records = consumer.poll(POLL_SIZE, POLL_TIMEOUT);
      for (final ZeebeRecord record : records) {
        final int partition = record.partitionId();
        final long committed =
            cachedCommittedOffset.computeIfAbsent(partition, icebergWriter::committedOffset);
        if (record.offset() <= committed) {
          // Belt-and-braces replay guard -- see the class javadoc's "Offset authority" section.
          continue;
        }
        final PartitionPipelines pipelines = partitionPipelines.get(partition);
        if (pipelines == null) {
          // Assignment race: the rebalance listener hasn't finished building this partition's
          // pipelines yet even though a record for it already arrived. Retry on a later poll tick
          // rather than dropping the record -- it stays unconsumed since we never advance past it.
          continue;
        }
        if (!translateRetrying(pipelines, record, running)) {
          return; // a pipeline failed terminally; see translateRetrying's own javadoc
        }
        lastAppliedOffset.put(partition, record.offset());
        lastAppliedTimestampMs.put(partition, record.record().getTimestamp());
      }

      // Called once per poll-loop tick for every owned partition, regardless of whether this tick's
      // poll() returned any of its records -- see SinkPipeline#onPollTick's own javadoc: it never
      // seals an empty segment, so this is harmless for an idle partition, and it's what lets the
      // TIME_DUE trigger fire during otherwise-quiet periods.
      //
      // Frontier choice: "last record's timestamp" rather than "min start of open instances" --
      // TranslatorState only exposes a full scan (forEachOpenInstance) to compute that minimum, not
      // an O(1) tracked value, so it is not "cheaply available" per Descriptor's own javadoc
      // ("min start of open instances, or the event-time watermark when none are open"); the last
      // processed record's own timestamp is the cheap fallback that javadoc anticipates.
      for (final Map.Entry<Integer, PartitionPipelines> entry : partitionPipelines.entrySet()) {
        final int partition = entry.getKey();
        entry
            .getValue()
            .onPollTick(
                lastAppliedOffset.getOrDefault(partition, -1L),
                lastAppliedTimestampMs.getOrDefault(partition, -1L));
      }

      // stateDumpIntervalMs == 0 disables the dump entirely. Runs on this same poll-loop thread --
      // no background thread -- so the dump's forEach scan never races a concurrent state mutation.
      // This is also the open_instances_gauge sampling cadence (see OpenInstancesGaugeSampler's own
      // javadoc): a stateDumpIntervalMs of 0 disables gauge sampling too, since the sample
      // piggybacks
      // on this same scan rather than paying for a second one.
      if (stateDumpIntervalMs > 0
          && System.currentTimeMillis() - lastStateDumpAtMs >= stateDumpIntervalMs) {
        final long tickNowMs = System.currentTimeMillis();
        // Capture offsets BEFORE the dumper iterates the open state -- see StateSnapshotDumper's
        // class javadoc for why that order is the whole consistency contract.
        final Map<String, Long> openCountsByProcessId =
            stateSnapshotDumper.dump(Map.copyOf(lastAppliedOffset));
        gaugeSampler.tick(tickNowMs, openCountsByProcessId);
        lastStateDumpAtMs = tickNowMs;
      }
      // compactIntervalMs == 0 disables compaction entirely. Runs on this same poll-loop thread,
      // between poll ticks, never concurrently with a commit from within this process -- see
      // LakeCompactor's class javadoc for why that is load-bearing.
      if (compactIntervalMs > 0
          && System.currentTimeMillis() - lastCompactAtMs >= compactIntervalMs) {
        LOG.info("Compaction result: {}", compactor.compactIfNeeded());
        // Same housekeeping cadence: delete files whose intent was journaled by the coordinator
        // (lost commit races, redelivered descriptors) -- see LakeCommitCoordinator's javadoc.
        try (final LocalFileIO io = new LocalFileIO()) {
          final int swept = coordinator.sweepPendingDeletes(io);
          if (swept > 0) {
            LOG.info("Swept {} orphaned files queued by the commit coordinator", swept);
          }
        }
        // Object lifecycle capture: same housekeeping cadence, same poll-thread-between-polls
        // placement as the two sweeps above -- a bounded full scan of the OBJECT_LIFECYCLE column
        // family (see TranslatorState#sweepObjectLifecycleTombstones's own javadoc), costing one
        // pause proportional to the number of distinct objects ever sighted (open + tombstoned),
        // the same class of cost StateSnapshotDumper's own forEachOpenInstance scan already pays on
        // this same thread.
        final int tombstonesSwept =
            state.sweepObjectLifecycleTombstones(
                System.currentTimeMillis() - objectTombstoneRetentionMs);
        if (tombstonesSwept > 0) {
          LOG.info("Swept {} closed object-lifecycle tombstone(s)", tombstonesSwept);
        }
        lastCompactAtMs = System.currentTimeMillis();
      }
    }
  }

  /**
   * Retries {@code record} against {@code pipelines}' translator until it is accepted (the
   * partition's own {@link BackpressureGate} pause/resume cycles it through) or a pipeline fails
   * terminally, or shutdown is requested mid-retry.
   *
   * @return {@code false} once a pipeline has failed terminally — the caller must stop the poll
   *     loop
   */
  private static boolean translateRetrying(
      final PartitionPipelines pipelines, final ZeebeRecord record, final AtomicBoolean running) {
    while (running.get()) {
      if (pipelines.isFailed()) {
        LOG.error(
            "L0 sink pipeline for partition {} failed terminally; stopping the poll loop",
            record.partitionId());
        return false;
      }
      if (pipelines.translate(record)) {
        return true;
      }
      LockSupport.parkNanos(PARK_NANOS_ON_BACKPRESSURE);
    }
    return true; // shutdown requested mid-retry; the shutdown hook drains whatever already landed
  }

  /**
   * Seeks each of {@code partitions} to this writer's own durably committed offset plus one,
   * caching that offset for the poll loop's replay guard, and lazily builds (idempotently, via
   * {@code computeIfAbsent}) each partition's {@link PartitionPipelines}. A no-op for an empty
   * collection (nothing owned yet, or an assignment delta with no gained partitions).
   */
  private static void seekTo(
      final ZeebeRecordConsumer consumer,
      final IcebergLakeWriter writer,
      final Map<Integer, Long> cachedCommittedOffset,
      final Collection<TopicPartition> partitions,
      final TranslatorState state,
      final SinkWiring wiring,
      final Map<Integer, PartitionPipelines> partitionPipelines) {
    if (partitions.isEmpty()) {
      return;
    }
    final Consumer rawConsumer = consumer.unwrap();
    final Map<TopicPartition, Long> positions = new HashMap<>();
    for (final TopicPartition tp : partitions) {
      final long committed = writer.committedOffset(tp.partition());
      cachedCommittedOffset.put(tp.partition(), committed);
      positions.put(tp, committed + 1);
      partitionPipelines.computeIfAbsent(
          tp.partition(),
          partition -> {
            final PartitionPipelines pipelines =
                buildPartitionPipelines(partition, tp, rawConsumer, state, wiring);
            // Runs exactly once, before this fresh translator ever sees a record -- see
            // #seedWatermarks' own javadoc for why reseeding an already-running translator would
            // be wrong.
            seedWatermarks(writer, pipelines);
            pipelines.start();
            return pipelines;
          });
    }
    consumer.seek(positions);
    LOG.info("Resuming partitions at {}", positions);
  }

  /**
   * Seeds {@code pipelines}' translator origin-position dedup watermarks from every {@code
   * lake.zbpos.z*} stamp durable on either raw table, using the same MIN-across-tables cut rule the
   * offset seek above uses (see {@link IcebergLakeWriter#committedZeebeWatermark(int)}'s own
   * javadoc). Must run exactly once, right after a partition's pipelines are first built — never on
   * a later reassignment of an already-running partition: reseeding a live translator from a
   * possibly-stale durable stamp would let the watermark regress and reopen the dedup window the
   * gate exists to close (see {@code LakeTranslator#seedWatermark}'s own javadoc).
   */
  private static void seedWatermarks(
      final IcebergLakeWriter writer, final PartitionPipelines pipelines) {
    for (final int zeebePartitionId : writer.stampedZeebePartitionIds()) {
      final long watermark = writer.committedZeebeWatermark(zeebePartitionId);
      if (watermark >= 0) {
        pipelines.seedWatermark(zeebePartitionId, watermark);
      }
    }
  }

  private static PartitionPipelines buildPartitionPipelines(
      final int partition,
      final TopicPartition topicPartition,
      final Consumer consumer,
      final TranslatorState state,
      final SinkWiring wiring) {
    // Shared by all three tables' pipelines for this partition -- see PartitionBackpressureGate's
    // own javadoc for why a plain 1:1 gate per ring would thrash pause/resume instead.
    final PartitionBackpressureGate gate = new PartitionBackpressureGate(consumer, topicPartition);
    // Riders are per-pipeline state (accumulators keyed by this partition's flush windows), so
    // each partition gets its own instances -- unlike the commit sink, which is shared. The
    // instances pipeline hosts SIX: three row-fed MetricsRiders (instance-completion metrics,
    // per-variant metrics, and cohort metrics -- the last rides completion rows keyed by the
    // instance's own start slot) plus three poll-fed riders (branch counts, started counters,
    // variable profiles) whose records never produce a raw row at all -- see PollFedRider's own
    // class javadoc for the boundary alignment this relies on. FlushLoop's rider list is a
    // List<SealRider>, and its drainRiders() already merges disjoint per-rider derived-table sets
    // (throwing on a clash) -- many riders on one pipeline is supported machinery, not something
    // bolted on here.
    final PollFedRider flowCountsRider =
        new PollFedRider(wiring.flowMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS);
    final PollFedRider startedCountsRider =
        new PollFedRider(
            wiring.instanceStartMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS);
    final PollFedRider profilesRider =
        new PollFedRider(wiring.profileMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS);
    // Object lifecycle capture: a fourth poll-fed rider, same reasoning as the three above -- an
    // object's birth (LakeTranslator#foldObjectLifecycleSighting) produces no raw row of its own.
    // Rides the instances pipeline too, purely for its flush-tick heartbeat -- like every other
    // poll-fed rider here, it has no logical connection to the instances TABLE itself.
    final PollFedRider objectsBornRider =
        new PollFedRider(
            wiring.objectsBornMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS);
    final SinkPipeline instancesPipeline =
        newPipeline(
            wiring.instancesSchema(),
            partition,
            gate,
            wiring.instancesEncoderFactory(),
            wiring.instancesSink(),
            List.of(
                new MetricsRider(
                    wiring.instanceMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS),
                new MetricsRider(
                    wiring.instanceVariantMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS),
                new MetricsRider(
                    wiring.instanceCohortMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS),
                flowCountsRider,
                startedCountsRider,
                profilesRider,
                objectsBornRider),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            SEGMENT_ROWS,
            RING_SEGMENTS,
            VARS_JSON_AVG_BYTES_PER_ROW);
    final SinkPipeline activitiesPipeline =
        newPipeline(
            wiring.activitiesSchema(),
            partition,
            gate,
            wiring.activitiesEncoderFactory(),
            wiring.activitiesSink(),
            List.of(
                new MetricsRider(
                    wiring.activityMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS)),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            SEGMENT_ROWS,
            RING_SEGMENTS,
            VARS_JSON_AVG_BYTES_PER_ROW);
    // Small, dedicated ring (see DICTIONARY_SEGMENT_ROWS/DICTIONARY_RING_SEGMENTS' own javadoc); no
    // riders -- the variants table is a plain dictionary, not a metrics source.
    final SinkPipeline variantsPipeline =
        newPipeline(
            wiring.variantsSchema(),
            partition,
            gate,
            wiring.variantsEncoderFactory(),
            wiring.variantsSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            DICTIONARY_BINARY_AVG_BYTES_PER_ROW);
    // OCPM object fabric: three more small, dedicated dictionary-kind rings -- no riders, same
    // reasoning as variantsPipeline above (see RawTableSchemas#objects/#instanceLinks/
    // #objectRelations' own javadocs).
    final SinkPipeline objectsPipeline =
        newPipeline(
            wiring.objectsSchema(),
            partition,
            gate,
            wiring.objectsEncoderFactory(),
            wiring.objectsSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            DICTIONARY_BINARY_AVG_BYTES_PER_ROW);
    final SinkPipeline instanceLinksPipeline =
        newPipeline(
            wiring.instanceLinksSchema(),
            partition,
            gate,
            wiring.instanceLinksEncoderFactory(),
            wiring.instanceLinksSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            DICTIONARY_BINARY_AVG_BYTES_PER_ROW);
    final SinkPipeline objectRelationsPipeline =
        newPipeline(
            wiring.objectRelationsSchema(),
            partition,
            gate,
            wiring.objectRelationsEncoderFactory(),
            wiring.objectRelationsSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            DICTIONARY_BINARY_AVG_BYTES_PER_ROW);
    // Object lifecycle capture: a fourth small, dedicated dictionary-kind ring, same reasoning as
    // objectsPipeline/instanceLinksPipeline/objectRelationsPipeline above -- but, unlike those
    // three, this one DOES carry a rider (object_cohorts rides its own closing rows).
    final SinkPipeline objectLifecyclePipeline =
        newPipeline(
            wiring.objectLifecycleSchema(),
            partition,
            gate,
            wiring.objectLifecycleEncoderFactory(),
            wiring.objectLifecycleSink(),
            List.of(
                new MetricsRider(
                    wiring.objectCohortMetrics(), wiring.partialsEncoderFactory(), SEGMENT_ROWS)),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            DICTIONARY_BINARY_AVG_BYTES_PER_ROW);
    // Dictionary-kind ring like objectsPipeline/instanceLinksPipeline/objectRelationsPipeline
    // above, no riders -- but its own byte budget (see
    // PROCESS_DEFINITIONS_BINARY_AVG_BYTES_PER_ROW's own javadoc), since bpmn_xml rows run far
    // larger than any other dictionary table's columns.
    final SinkPipeline processDefinitionsPipeline =
        newPipeline(
            wiring.processDefinitionsSchema(),
            partition,
            gate,
            wiring.processDefinitionsEncoderFactory(),
            wiring.processDefinitionsSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry(),
            DICTIONARY_SEGMENT_ROWS,
            DICTIONARY_RING_SEGMENTS,
            PROCESS_DEFINITIONS_BINARY_AVG_BYTES_PER_ROW);
    final RowAppender instanceAppender = new SegmentRowAppender(instancesPipeline.ring());
    final RowAppender activityAppender = new SegmentRowAppender(activitiesPipeline.ring());
    final RowAppender variantsAppender = new SegmentRowAppender(variantsPipeline.ring());
    final RowAppender objectsAppender = new SegmentRowAppender(objectsPipeline.ring());
    final RowAppender instanceLinksAppender = new SegmentRowAppender(instanceLinksPipeline.ring());
    final RowAppender objectRelationsAppender =
        new SegmentRowAppender(objectRelationsPipeline.ring());
    final RowAppender objectLifecycleAppender =
        new SegmentRowAppender(objectLifecyclePipeline.ring());
    final RowAppender processDefinitionsAppender =
        new SegmentRowAppender(processDefinitionsPipeline.ring());
    final LakeTranslator translator =
        new LakeTranslator(
            state,
            instanceAppender,
            activityAppender,
            variantsAppender,
            flowCountsRider,
            startedCountsRider,
            profilesRider,
            objectsAppender,
            instanceLinksAppender,
            objectRelationsAppender,
            wiring.objectTypes(),
            objectLifecycleAppender,
            objectsBornRider,
            processDefinitionsAppender);
    return new PartitionPipelines(
        instancesPipeline,
        activitiesPipeline,
        variantsPipeline,
        objectsPipeline,
        instanceLinksPipeline,
        objectRelationsPipeline,
        objectLifecyclePipeline,
        processDefinitionsPipeline,
        translator);
  }

  private static SinkPipeline newPipeline(
      final TableSchema schema,
      final int partition,
      final BackpressureGate gate,
      final BatchEncoder.Factory encoderFactory,
      final DescriptorSink descriptorSink,
      final List<SealRider> riders,
      final long flushIntervalMs,
      final MeterRegistry meterRegistry,
      final int segmentRows,
      final int ringSegments,
      final int binaryAvgBytesPerRowValue) {
    final int[] binaryAvgBytesPerRow = new int[schema.columns().size()];
    for (int i = 0; i < binaryAvgBytesPerRow.length; i++) {
      if (schema.columns().get(i).type() == ColumnType.BINARY) {
        binaryAvgBytesPerRow[i] = binaryAvgBytesPerRowValue;
      }
    }
    final Interner interner = new Interner();
    final Segment[] segments =
        SegmentFactory.createSegments(
            schema, ringSegments, segmentRows, binaryAvgBytesPerRow, interner);
    final SegmentSorter sorter =
        new SegmentSorter(schema, segmentRows, binaryAvgBytesPerRow, interner);
    final SinkConfig config =
        new SinkConfig(
            segmentRows,
            ringSegments,
            flushIntervalMs,
            FILE_TARGET_BYTES,
            partition,
            schema.table());
    return new SinkPipeline(
        config,
        segments,
        gate,
        sorter::sort,
        encoderFactory,
        descriptorSink,
        riders,
        System::currentTimeMillis,
        meterRegistry);
  }

  private static LakeConfig configFromSystemProperties() {
    final String bpmnDirProperty = System.getProperty("lake.bpmnDir");
    return new LakeConfig(
        System.getProperty("lake.contactPoint", "http://localhost:8080"),
        System.getProperty("lake.topic", "zeebe-records"),
        System.getProperty("lake.group", "lake-poc"),
        Path.of(System.getProperty("lake.dir", "./data/lake")),
        Path.of("./data/lake-state"),
        Integer.getInteger("lake.flushRows", 5000),
        Long.getLong("lake.flushIntervalMs", 30000L),
        Long.getLong("lake.stateDumpIntervalMs", 30000L),
        Long.getLong("lake.compactIntervalMs", 300000L),
        Integer.getInteger("lake.uiPort", 8091),
        bpmnDirProperty == null ? null : Path.of(bpmnDirProperty),
        Long.getLong(
            "lake.objectTombstoneRetentionMs", LakeConfig.DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS),
        Long.getLong("lake.gaugeFlushIntervalMs", LakeConfig.DEFAULT_GAUGE_FLUSH_INTERVAL_MS));
  }

  /**
   * Per-table plumbing shared by every partition's {@link SinkPipeline} pair: the {@link
   * TableSchema}s (field ids resolved once from the live catalog), the encoder factories and the
   * shared {@link CoordinatedDescriptorSink} (safe across partitions — see its javadoc), the
   * activities metrics declaration each partition builds its own {@link MetricsRider} from, the
   * configured flush interval, and the {@link MeterRegistry} every pipeline's metrics register
   * into.
   */
  private record SinkWiring(
      TableSchema instancesSchema,
      TableSchema activitiesSchema,
      TableSchema variantsSchema,
      TableSchema objectsSchema,
      TableSchema instanceLinksSchema,
      TableSchema objectRelationsSchema,
      TableSchema objectLifecycleSchema,
      TableSchema processDefinitionsSchema,
      IcebergParquetEncoderFactory instancesEncoderFactory,
      IcebergParquetEncoderFactory activitiesEncoderFactory,
      IcebergParquetEncoderFactory variantsEncoderFactory,
      IcebergParquetEncoderFactory objectsEncoderFactory,
      IcebergParquetEncoderFactory instanceLinksEncoderFactory,
      IcebergParquetEncoderFactory objectRelationsEncoderFactory,
      IcebergParquetEncoderFactory objectLifecycleEncoderFactory,
      IcebergParquetEncoderFactory processDefinitionsEncoderFactory,
      DescriptorSink instancesSink,
      DescriptorSink activitiesSink,
      DescriptorSink variantsSink,
      DescriptorSink objectsSink,
      DescriptorSink instanceLinksSink,
      DescriptorSink objectRelationsSink,
      DescriptorSink objectLifecycleSink,
      DescriptorSink processDefinitionsSink,
      CompiledEntityMetrics instanceMetrics,
      CompiledEntityMetrics activityMetrics,
      CompiledEntityMetrics instanceVariantMetrics,
      CompiledEntityMetrics flowMetrics,
      CompiledEntityMetrics instanceStartMetrics,
      CompiledEntityMetrics instanceCohortMetrics,
      CompiledEntityMetrics profileMetrics,
      CompiledEntityMetrics objectsBornMetrics,
      CompiledEntityMetrics objectCohortMetrics,
      BatchEncoder.Factory partialsEncoderFactory,
      LakeCommitCoordinator coordinator,
      long flushIntervalMs,
      MeterRegistry meterRegistry,
      CompiledObjectTypes objectTypes) {}

  /**
   * One owned partition's L0 sink wiring: the three per-table {@link SinkPipeline}s (one per
   * (table, partition), per {@link SinkConfig}'s own contract) and the {@link LakeTranslator}
   * appending into them.
   */
  private static final class PartitionPipelines {
    private final SinkPipeline instancesPipeline;
    private final SinkPipeline activitiesPipeline;
    private final SinkPipeline variantsPipeline;
    private final SinkPipeline objectsPipeline;
    private final SinkPipeline instanceLinksPipeline;
    private final SinkPipeline objectRelationsPipeline;
    private final SinkPipeline objectLifecyclePipeline;
    private final SinkPipeline processDefinitionsPipeline;
    private final LakeTranslator translator;

    private PartitionPipelines(
        final SinkPipeline instancesPipeline,
        final SinkPipeline activitiesPipeline,
        final SinkPipeline variantsPipeline,
        final SinkPipeline objectsPipeline,
        final SinkPipeline instanceLinksPipeline,
        final SinkPipeline objectRelationsPipeline,
        final SinkPipeline objectLifecyclePipeline,
        final SinkPipeline processDefinitionsPipeline,
        final LakeTranslator translator) {
      this.instancesPipeline = instancesPipeline;
      this.activitiesPipeline = activitiesPipeline;
      this.variantsPipeline = variantsPipeline;
      this.objectsPipeline = objectsPipeline;
      this.instanceLinksPipeline = instanceLinksPipeline;
      this.objectRelationsPipeline = objectRelationsPipeline;
      this.objectLifecyclePipeline = objectLifecyclePipeline;
      this.processDefinitionsPipeline = processDefinitionsPipeline;
      this.translator = translator;
    }

    void start() {
      instancesPipeline.start();
      activitiesPipeline.start();
      variantsPipeline.start();
      objectsPipeline.start();
      instanceLinksPipeline.start();
      objectRelationsPipeline.start();
      objectLifecyclePipeline.start();
      processDefinitionsPipeline.start();
    }

    boolean translate(final ZeebeRecord record) {
      return translator.onRecord(record);
    }

    void onPollTick(final long lastOffset, final long frontierMs) {
      // One immutable snapshot per tick (see LakeTranslator#watermarkSnapshot's own javadoc for
      // the allocation budget), shared by every table's pipeline -- they always cover the exact
      // same set of folded Zeebe records for this owned partition.
      final Map<Integer, Long> watermarks = translator.watermarkSnapshot();
      instancesPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      activitiesPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      variantsPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      objectsPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      instanceLinksPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      objectRelationsPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      objectLifecyclePipeline.onPollTick(lastOffset, frontierMs, watermarks);
      processDefinitionsPipeline.onPollTick(lastOffset, frontierMs, watermarks);
    }

    /** See {@code LakePocApp#seedWatermarks}. */
    void seedWatermark(final int zeebePartitionId, final long position) {
      translator.seedWatermark(zeebePartitionId, position);
    }

    boolean isFailed() {
      return instancesPipeline.isFailed()
          || activitiesPipeline.isFailed()
          || variantsPipeline.isFailed()
          || objectsPipeline.isFailed()
          || instanceLinksPipeline.isFailed()
          || objectRelationsPipeline.isFailed()
          || objectLifecyclePipeline.isFailed()
          || processDefinitionsPipeline.isFailed();
    }

    void close() {
      instancesPipeline.close();
      activitiesPipeline.close();
      variantsPipeline.close();
      objectsPipeline.close();
      instanceLinksPipeline.close();
      objectRelationsPipeline.close();
      objectLifecyclePipeline.close();
      processDefinitionsPipeline.close();
    }
  }

  /**
   * Reference-counted {@link BackpressureGate} over one Event Bridge partition's consumer
   * pause/resume: both the instances-table and activities-table {@link SinkPipeline}s for the same
   * partition share one instance, so one ring filling up pauses the partition exactly once, and the
   * partition only resumes once every ring sharing this gate has room again. A plain 1:1 gate per
   * ring would let one ring's {@code resume()} re-enable consumption while the other ring is still
   * full, thrashing pause/resume every tick until the second ring also frees up.
   */
  private static final class PartitionBackpressureGate implements BackpressureGate {
    private final Consumer consumer;
    private final List<TopicPartition> partitions;
    private final AtomicInteger pausedRingCount = new AtomicInteger();

    private PartitionBackpressureGate(final Consumer consumer, final TopicPartition partition) {
      this.consumer = consumer;
      partitions = List.of(partition);
    }

    @Override
    public void pause() {
      if (pausedRingCount.incrementAndGet() == 1) {
        consumer.pause(partitions);
      }
    }

    @Override
    public void resume() {
      if (pausedRingCount.decrementAndGet() == 0) {
        consumer.resume(partitions);
      }
    }
  }
}
