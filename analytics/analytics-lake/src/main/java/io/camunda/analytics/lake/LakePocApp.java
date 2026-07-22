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
 * LakeCompactor}/{@code GoldTables} still drive for compaction/gold-table work are all still needed
 * — but its buffered {@code append}/{@code flush} raw-ingest path is never called from this class
 * anymore. Instead, each owned partition gets its own {@link SinkPipeline} pair, fed directly by
 * {@link LakeTranslator} through a {@link RowAppender}, flushed on its own schedule by its own
 * flush thread, and committed to the {@code instances}/{@code activities} {@link Table}s (plus the
 * rider-derived partials tables) via one shared {@link CoordinatedDescriptorSink} — every
 * descriptor becomes one atomic catalog transaction through {@link LakeCommitCoordinator}, whose
 * database row locks serialize concurrent committers (multiple partitions' flush threads, and
 * {@link LakeCompactor}'s own poll-thread commits).
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

  private static final long PARK_NANOS_ON_BACKPRESSURE = 1_000_000L; // 1ms

  private LakePocApp() {}

  public static void main(final String[] args) {
    final LakeConfig config = configFromSystemProperties();
    LOG.info(
        "Starting lake PoC translator: gateway={} topic={} group={} warehouse={} state={} "
            + "flushIntervalMs={} stateDumpIntervalMs={} compactIntervalMs={} uiPort={}",
        config.contactPoint(),
        config.topic(),
        config.consumerGroup(),
        config.warehouseDir(),
        config.stateDir(),
        config.flushIntervalMs(),
        config.stateDumpIntervalMs(),
        config.compactIntervalMs(),
        config.uiPort());

    final EventBridgeClient client = EventBridgeClient.create(config.contactPoint());
    final IcebergLakeWriter icebergWriter = new IcebergLakeWriter(config);
    final TranslatorState state = new RocksDbTranslatorState(config.stateDir());
    final StateSnapshotDumper stateSnapshotDumper =
        new StateSnapshotDumper(state, config.stateDir().resolve("_snapshot"));
    final LakeCompactor compactor = new LakeCompactor(icebergWriter);

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
    final Thread mainThread = Thread.currentThread();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutdown requested; draining poll loop");
                  running.set(false);
                  try {
                    // Joining the main thread establishes happens-before for everything it wrote
                    // before exiting its loop, so no extra synchronization is needed to safely
                    // touch partitionPipelines/state/writer from here afterward.
                    mainThread.join(SHUTDOWN_DRAIN_TIMEOUT.toMillis());
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  // Draining each pipeline seals whatever the filling segment holds, waits for the
                  // flush thread to finalize files and commit a last descriptor, then stops --
                  // see SinkPipeline#close's own javadoc. Must happen before the writer (and its
                  // catalog/DuckDB connection) closes.
                  for (final PartitionPipelines pipelines : partitionPipelines.values()) {
                    pipelines.close();
                  }
                  if (uiServer != null) {
                    uiServer.close();
                  }
                  state.close();
                  icebergWriter.close();
                  consumer.close();
                  client.close();
                  LOG.info("Lake PoC translator stopped");
                },
                "lake-poc-shutdown"));

    runLoop(
        consumer,
        icebergWriter,
        cachedCommittedOffset,
        partitionPipelines,
        running,
        stateSnapshotDumper,
        compactor,
        wiring.coordinator(),
        config.stateDumpIntervalMs(),
        config.compactIntervalMs());
  }

  /** Builds the per-table plumbing shared by every partition's {@link SinkPipeline} pair. */
  private static SinkWiring buildSinkWiring(
      final LakeConfig config, final IcebergLakeWriter writer) {
    final Table instancesTable = writer.instancesTable();
    final Table activitiesTable = writer.activitiesTable();
    final TableSchema activitiesSchema = RawTableSchemas.activities(activitiesTable.schema());

    // The activities metrics declaration -- dims, window, measures; everything downstream
    // (partials schemas, tables, rider plans, merge/finalize SQL, fingerprint) derives from it.
    final CompiledEntityMetrics activityMetrics =
        EntityMetrics.declare("activities", activitiesSchema)
            .dims("process_id", "element_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    final Table metricsTable =
        writer.partialsTableOrCreate(
            activityMetrics.metricsSchema(), activityMetrics.fingerprint());
    final Table histTable =
        writer.partialsTableOrCreate(activityMetrics.histSchema(), activityMetrics.fingerprint());

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
    // The rider's one factory dispatches by generated-schema table name -- each partials table has
    // its own iceberg schema and thus its own underlying encoder factory.
    final Map<String, BatchEncoder.Factory> partialsFactories =
        Map.of(
            activityMetrics.metricsSchema().table(),
            new IcebergParquetEncoderFactory(
                metricsTable.schema(), fileSink, SEGMENT_ROWS, Set.of()),
            activityMetrics.histSchema().table(),
            new IcebergParquetEncoderFactory(histTable.schema(), fileSink, SEGMENT_ROWS, Set.of()));
    final BatchEncoder.Factory partialsEncoderFactory =
        (schema, epochDay) -> partialsFactories.get(schema.table()).newFile(schema, epochDay);

    // Every pipeline commits through the one coordinator: raw-only descriptors are a batch of one,
    // rider-carrying descriptors fan out atomically -- a single commit path either way.
    final LakeCommitCoordinator coordinator = new LakeCommitCoordinator(writer.jdbcUrl(), "lake");
    final Map<String, Table> tablesByName =
        Map.of(
            "instances",
            instancesTable,
            "activities",
            activitiesTable,
            activityMetrics.metricsSchema().table(),
            metricsTable,
            activityMetrics.histSchema().table(),
            histTable);
    final CoordinatedDescriptorSink commitSink =
        new CoordinatedDescriptorSink(coordinator, Namespace.of("lake"), tablesByName::get);

    return new SinkWiring(
        RawTableSchemas.instances(instancesTable.schema()),
        activitiesSchema,
        instancesEncoderFactory,
        activitiesEncoderFactory,
        commitSink,
        commitSink,
        activityMetrics,
        partialsEncoderFactory,
        coordinator,
        config.flushIntervalMs(),
        new SimpleMeterRegistry());
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
      final long stateDumpIntervalMs,
      final long compactIntervalMs) {
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
      if (stateDumpIntervalMs > 0
          && System.currentTimeMillis() - lastStateDumpAtMs >= stateDumpIntervalMs) {
        // Capture offsets BEFORE the dumper iterates the open state -- see StateSnapshotDumper's
        // class javadoc for why that order is the whole consistency contract.
        stateSnapshotDumper.dump(Map.copyOf(lastAppliedOffset));
        lastStateDumpAtMs = System.currentTimeMillis();
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
    // Shared by both tables' pipelines for this partition -- see PartitionBackpressureGate's own
    // javadoc for why a plain 1:1 gate per ring would thrash pause/resume instead.
    final PartitionBackpressureGate gate = new PartitionBackpressureGate(consumer, topicPartition);
    final SinkPipeline instancesPipeline =
        newPipeline(
            wiring.instancesSchema(),
            partition,
            gate,
            wiring.instancesEncoderFactory(),
            wiring.instancesSink(),
            List.of(),
            wiring.flushIntervalMs(),
            wiring.meterRegistry());
    // The rider is per-pipeline state (accumulators keyed by this partition's flush windows), so
    // each partition gets its own instance -- unlike the commit sink, which is shared.
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
            wiring.meterRegistry());
    final RowAppender instanceAppender = new SegmentRowAppender(instancesPipeline.ring());
    final RowAppender activityAppender = new SegmentRowAppender(activitiesPipeline.ring());
    final LakeTranslator translator = new LakeTranslator(state, instanceAppender, activityAppender);
    return new PartitionPipelines(instancesPipeline, activitiesPipeline, translator);
  }

  private static SinkPipeline newPipeline(
      final TableSchema schema,
      final int partition,
      final BackpressureGate gate,
      final BatchEncoder.Factory encoderFactory,
      final DescriptorSink descriptorSink,
      final List<SealRider> riders,
      final long flushIntervalMs,
      final MeterRegistry meterRegistry) {
    final int[] binaryAvgBytesPerRow = new int[schema.columns().size()];
    for (int i = 0; i < binaryAvgBytesPerRow.length; i++) {
      if (schema.columns().get(i).type() == ColumnType.BINARY) {
        binaryAvgBytesPerRow[i] = VARS_JSON_AVG_BYTES_PER_ROW;
      }
    }
    final Interner interner = new Interner();
    final Segment[] segments =
        SegmentFactory.createSegments(
            schema, RING_SEGMENTS, SEGMENT_ROWS, binaryAvgBytesPerRow, interner);
    final SegmentSorter sorter =
        new SegmentSorter(schema, SEGMENT_ROWS, binaryAvgBytesPerRow, interner);
    final SinkConfig config =
        new SinkConfig(
            SEGMENT_ROWS,
            RING_SEGMENTS,
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
        bpmnDirProperty == null ? null : Path.of(bpmnDirProperty));
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
      IcebergParquetEncoderFactory instancesEncoderFactory,
      IcebergParquetEncoderFactory activitiesEncoderFactory,
      DescriptorSink instancesSink,
      DescriptorSink activitiesSink,
      CompiledEntityMetrics activityMetrics,
      BatchEncoder.Factory partialsEncoderFactory,
      LakeCommitCoordinator coordinator,
      long flushIntervalMs,
      MeterRegistry meterRegistry) {}

  /**
   * One owned partition's L0 sink wiring: the two per-table {@link SinkPipeline}s (one per (table,
   * partition), per {@link SinkConfig}'s own contract) and the {@link LakeTranslator} appending
   * into them.
   */
  private static final class PartitionPipelines {
    private final SinkPipeline instancesPipeline;
    private final SinkPipeline activitiesPipeline;
    private final LakeTranslator translator;

    private PartitionPipelines(
        final SinkPipeline instancesPipeline,
        final SinkPipeline activitiesPipeline,
        final LakeTranslator translator) {
      this.instancesPipeline = instancesPipeline;
      this.activitiesPipeline = activitiesPipeline;
      this.translator = translator;
    }

    void start() {
      instancesPipeline.start();
      activitiesPipeline.start();
    }

    boolean translate(final ZeebeRecord record) {
      return translator.onRecord(record);
    }

    void onPollTick(final long lastOffset, final long frontierMs) {
      // One immutable snapshot per tick (see LakeTranslator#watermarkSnapshot's own javadoc for
      // the allocation budget), shared by both tables' pipelines -- they always cover the exact
      // same set of folded Zeebe records for this owned partition.
      final Map<Integer, Long> watermarks = translator.watermarkSnapshot();
      instancesPipeline.onPollTick(lastOffset, frontierMs, watermarks);
      activitiesPipeline.onPollTick(lastOffset, frontierMs, watermarks);
    }

    /** See {@code LakePocApp#seedWatermarks}. */
    void seedWatermark(final int zeebePartitionId, final long position) {
      translator.seedWatermark(zeebePartitionId, position);
    }

    boolean isFailed() {
      return instancesPipeline.isFailed() || activitiesPipeline.isFailed();
    }

    void close() {
      instancesPipeline.close();
      activitiesPipeline.close();
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
