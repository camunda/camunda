/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake;

import io.camunda.analytics.lake.state.RocksDbTranslatorState;
import io.camunda.analytics.lake.state.StateSnapshotDumper;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.translate.LakeTranslator;
import io.camunda.analytics.lake.ui.LakeUiServer;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import io.camunda.analytics.lake.write.LakeCompactor;
import io.camunda.analytics.lake.write.LakeWriter;
import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the lake PoC: subscribes to Zeebe records from the Event Bridge as its own
 * consumer group, folds them through {@link LakeTranslator} into an {@link IcebergLakeWriter}, and
 * owns the flush policy that the {@link LakeWriter} contract delegates to its caller.
 *
 * <h2>Offset authority</h2>
 *
 * <p>This app does <em>not</em> rely on the Event Bridge consumer-group protocol's own server-side
 * committed offset ({@link Consumer#commitOffset}) for correctness — the lake's snapshot summary is
 * the sole durable offset authority (see {@link LakeWriter}). At startup, and on every partition
 * (re)assignment, this app explicitly {@link Consumer#seek}s to {@link
 * LakeWriter#committedOffset(int)} {@code + 1}. The coordinator is used only for group membership
 * and partition assignment, never for resume position. As a second, cheap line of defense, every
 * polled record is also checked against a locally cached committed offset before being processed —
 * belt and braces, in case a seek is ever missed (e.g. a narrow race on a rebalance).
 *
 * <h2>One partition buffered at a time</h2>
 *
 * <p>{@link IcebergLakeWriter} buffers rows for exactly one source partition between flushes (see
 * its class javadoc) — this app is what upholds that constraint. {@link FlushState} tracks which
 * partition's rows are currently buffered and forces a flush the moment the next record belongs to
 * a different partition, in addition to the row-count and time-based flush triggers.
 */
public final class LakePocApp {

  private static final Logger LOG = LoggerFactory.getLogger(LakePocApp.class);

  private static final int POLL_SIZE = 500;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(10);

  private LakePocApp() {}

  public static void main(final String[] args) {
    final LakeConfig config = configFromSystemProperties();
    LOG.info(
        "Starting lake PoC translator: gateway={} topic={} group={} warehouse={} state={} "
            + "flushRows={} flushIntervalMs={} stateDumpIntervalMs={} compactIntervalMs={} "
            + "uiPort={}",
        config.contactPoint(),
        config.topic(),
        config.consumerGroup(),
        config.warehouseDir(),
        config.stateDir(),
        config.flushRows(),
        config.flushIntervalMs(),
        config.stateDumpIntervalMs(),
        config.compactIntervalMs(),
        config.uiPort());

    final EventBridgeClient client = EventBridgeClient.create(config.contactPoint());
    final IcebergLakeWriter icebergWriter = new IcebergLakeWriter(config);
    final LakeWriter writer = icebergWriter;
    final TranslatorState state = new RocksDbTranslatorState(config.stateDir());
    final LakeTranslator translator = new LakeTranslator(state, writer);
    final StateSnapshotDumper stateSnapshotDumper =
        new StateSnapshotDumper(state, config.stateDir().resolve("_snapshot"));
    final LakeCompactor compactor = new LakeCompactor(icebergWriter);

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

    // Register the listener BEFORE the first heartbeat so the initial assignment is observed
    // through it too, per RebalanceListener's own javadoc contract ("set it before joinGroup/first
    // heartbeat"). This covers reassignment for the lifetime of the run (scale-out, this member
    // losing and regaining a partition, etc).
    rawConsumer.rebalanceListener(
        new RebalanceListener() {
          @Override
          public void onPartitionsAssigned(final Collection<TopicPartition> assigned) {
            seekTo(consumer, writer, cachedCommittedOffset, assigned);
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
    seekTo(consumer, writer, cachedCommittedOffset, rawConsumer.getOwnedPartitions());
    LOG.info(
        "Startup summary: owns {} partition(s): {}",
        rawConsumer.getOwnedPartitions().size(),
        rawConsumer.getOwnedPartitions());

    final AtomicBoolean running = new AtomicBoolean(true);
    final FlushState flushState = new FlushState();
    final Thread mainThread = Thread.currentThread();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutdown requested; draining poll loop");
                  running.set(false);
                  try {
                    // Joining the main thread establishes happens-before for everything it wrote
                    // to flushState before exiting its loop, so no extra synchronization is needed
                    // to safely read/flush it from here afterward.
                    mainThread.join(SHUTDOWN_DRAIN_TIMEOUT.toMillis());
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  if (flushState.hasPending) {
                    flushNow(writer, cachedCommittedOffset, flushState);
                  }
                  if (uiServer != null) {
                    uiServer.close();
                  }
                  state.close();
                  writer.close();
                  consumer.close();
                  client.close();
                  LOG.info("Lake PoC translator stopped");
                },
                "lake-poc-shutdown"));

    runLoop(
        consumer,
        translator,
        writer,
        cachedCommittedOffset,
        config,
        running,
        flushState,
        stateSnapshotDumper,
        compactor);
  }

  private static void runLoop(
      final ZeebeRecordConsumer consumer,
      final LakeTranslator translator,
      final LakeWriter writer,
      final Map<Integer, Long> cachedCommittedOffset,
      final LakeConfig config,
      final AtomicBoolean running,
      final FlushState flushState,
      final StateSnapshotDumper stateSnapshotDumper,
      final LakeCompactor compactor) {
    // Single-threaded: only this loop mutates it, so a plain HashMap (not the ConcurrentHashMap
    // used for cachedCommittedOffset, which the rebalance-listener thread also touches) suffices.
    final Map<Integer, Long> lastAppliedOffset = new HashMap<>();
    long lastStateDumpAtMs = System.currentTimeMillis();
    long lastCompactAtMs = System.currentTimeMillis();
    while (running.get()) {
      final List<ZeebeRecord> records = consumer.poll(POLL_SIZE, POLL_TIMEOUT);
      for (final ZeebeRecord record : records) {
        final int partition = record.partitionId();
        final long committed =
            cachedCommittedOffset.computeIfAbsent(partition, writer::committedOffset);
        if (record.offset() <= committed) {
          // Belt-and-braces replay guard -- see the class javadoc's "Offset authority" section.
          continue;
        }
        if (flushState.hasPending && partition != flushState.currentPartition) {
          // The writer only ever buffers rows for one source partition at a time; flush before
          // folding in the first record of a different partition.
          flushNow(writer, cachedCommittedOffset, flushState);
        }
        translator.onRecord(record);
        lastAppliedOffset.put(partition, record.offset());
        flushState.recordRow(partition, record.offset());
        if (flushState.rowsSinceFlush >= config.flushRows()) {
          flushNow(writer, cachedCommittedOffset, flushState);
        }
      }
      if (flushState.hasPending
          && System.currentTimeMillis() - flushState.lastFlushAtMs >= config.flushIntervalMs()) {
        flushNow(writer, cachedCommittedOffset, flushState);
      }
      // stateDumpIntervalMs == 0 disables the dump entirely. Runs on this same poll-loop thread --
      // no background thread -- so the dump's forEach scan never races a concurrent state mutation.
      if (config.stateDumpIntervalMs() > 0
          && System.currentTimeMillis() - lastStateDumpAtMs >= config.stateDumpIntervalMs()) {
        // Capture offsets BEFORE the dumper iterates the open state -- see StateSnapshotDumper's
        // class javadoc for why that order is the whole consistency contract.
        stateSnapshotDumper.dump(Map.copyOf(lastAppliedOffset));
        lastStateDumpAtMs = System.currentTimeMillis();
      }
      // compactIntervalMs == 0 disables compaction entirely. Runs on this same poll-loop thread,
      // between flushes, never concurrently with a commit from within this process -- see
      // LakeCompactor's class javadoc for why that is load-bearing.
      if (config.compactIntervalMs() > 0
          && System.currentTimeMillis() - lastCompactAtMs >= config.compactIntervalMs()) {
        LOG.info("Compaction result: {}", compactor.compactIfNeeded());
        lastCompactAtMs = System.currentTimeMillis();
      }
    }
  }

  private static void flushNow(
      final LakeWriter writer,
      final Map<Integer, Long> cachedCommittedOffset,
      final FlushState flushState) {
    final int partition = flushState.currentPartition;
    final long throughOffset = flushState.lastOffset;
    final long rows = flushState.rowsSinceFlush;
    writer.flush(partition, throughOffset);
    final long newCommitted = writer.committedOffset(partition);
    cachedCommittedOffset.put(partition, newCommitted);
    LOG.info(
        "Flushed partition {}: {} record(s) through offset {} (writer committedOffset now {})",
        partition,
        rows,
        throughOffset,
        newCommitted);
    flushState.reset();
  }

  /**
   * Seeks each of {@code partitions} to this writer's own durably committed offset plus one,
   * caching that offset for the poll loop's replay guard. A no-op for an empty collection (nothing
   * owned yet, or an assignment delta with no gained partitions).
   */
  private static void seekTo(
      final ZeebeRecordConsumer consumer,
      final LakeWriter writer,
      final Map<Integer, Long> cachedCommittedOffset,
      final Collection<TopicPartition> partitions) {
    if (partitions.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> positions = new HashMap<>();
    for (final TopicPartition tp : partitions) {
      final long committed = writer.committedOffset(tp.partition());
      cachedCommittedOffset.put(tp.partition(), committed);
      positions.put(tp, committed + 1);
    }
    consumer.seek(positions);
    LOG.info("Resuming partitions at {}", positions);
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
        Long.getLong("lake.flushIntervalMs", 2000L),
        Long.getLong("lake.stateDumpIntervalMs", 30000L),
        Long.getLong("lake.compactIntervalMs", 300000L),
        Integer.getInteger("lake.uiPort", 8091),
        bpmnDirProperty == null ? null : Path.of(bpmnDirProperty));
  }

  /**
   * Mutable flush bookkeeping. Written only from the poll loop thread ({@link #runLoop}), and read
   * from the shutdown-hook thread only after {@code mainThread.join()} has returned — see the
   * shutdown hook in {@link #main} for why that ordering makes this safe without explicit locking.
   */
  private static final class FlushState {
    private boolean hasPending;
    private int currentPartition;
    private long rowsSinceFlush;
    private long lastOffset;
    private long lastFlushAtMs = System.currentTimeMillis();

    void recordRow(final int partition, final long offset) {
      hasPending = true;
      currentPartition = partition;
      rowsSinceFlush++;
      lastOffset = offset;
    }

    void reset() {
      hasPending = false;
      rowsSinceFlush = 0;
      lastFlushAtMs = System.currentTimeMillis();
    }
  }
}
