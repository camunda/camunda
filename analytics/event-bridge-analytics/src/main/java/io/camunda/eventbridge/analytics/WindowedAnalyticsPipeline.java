/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.eventbridge.analytics.projection.BaseProjectionStore;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the consumer through one {@link StreamProcessor}: poll the assigned {@code zeebe-records}
 * partitions, fold each record once (the base-projection {@code ProcessExecutionProjector}), fan
 * the derived facts out to the registered rollups, flush the rollups once per batch (the wall-clock
 * tick), record the per-partition consumed position, and commit the source offset.
 *
 * <p>On start the consumer is seeked to the base projection's checkpointed positions, so a restart
 * resumes the fold from where it left off (start-from-offset) — no replay on the common path, and
 * the source log itself is the recovery log if local state is lost. Run multiple instances with the
 * same consumer group and distinct consumer ids: the bridge coordinator assigns source partitions
 * across them and rebalances on membership change. Each fact reaches the serving store once per key
 * per flush; advancing the offset only after a flush means a crash mid-batch replays rather than
 * loses, and the durable rollups dedup any replayed facts.
 */
public final class WindowedAnalyticsPipeline implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WindowedAnalyticsPipeline.class);
  private static final int MAX_RECORDS = 100;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private final ZeebeRecordConsumer sourceConsumer;
  private final StreamProcessor<ZeebeRecord> processor;
  private final BaseProjectionStore projectionStore;
  private final String sourceTopic;

  private volatile boolean running;
  private Thread thread;

  public WindowedAnalyticsPipeline(
      final ZeebeRecordConsumer sourceConsumer,
      final StreamProcessor<ZeebeRecord> processor,
      final BaseProjectionStore projectionStore,
      final String sourceTopic) {
    this.sourceConsumer = sourceConsumer;
    this.processor = processor;
    this.projectionStore = projectionStore;
    this.sourceTopic = sourceTopic;
  }

  public void start() {
    running = true;
    seekToCheckpointedPositions();
    thread = new Thread(this::run, "analytics-pipeline");
    thread.setDaemon(true);
    thread.start();
  }

  /** Resume each partition just past the last position the base projection durably folded. */
  private void seekToCheckpointedPositions() {
    final Map<Integer, Long> consumed = projectionStore.consumedPositions();
    if (consumed.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> startPositions = new HashMap<>();
    consumed.forEach(
        (partition, position) ->
            startPositions.put(new TopicPartition(sourceTopic, partition), position + 1));
    sourceConsumer.seek(startPositions);
    LOG.info("Resuming {} partition(s) from checkpointed positions {}", consumed.size(), consumed);
  }

  private void run() {
    processor.init();
    while (running) {
      try {
        final List<ZeebeRecord> records = sourceConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        if (records.isEmpty()) {
          continue;
        }
        // fold the whole batch into the rollups' in-memory combiners (no DB writes yet)
        for (final ZeebeRecord record : records) {
          processor.process(record);
        }
        // drain the combiners to the serving store: one upsert per group key, not per record
        processor.flush();
        // record how far the fold has consumed (start-from-offset on restart), then commit
        checkpointConsumedPositions(records);
        for (final ZeebeRecord record : records) {
          sourceConsumer.commit(record).join();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Analytics pipeline poll failed; backing off", e);
        sleep();
      }
    }
  }

  /**
   * Advance the base projection's consumed position to the max offset seen per source partition.
   */
  private void checkpointConsumedPositions(final List<ZeebeRecord> records) {
    final Map<Integer, Long> maxByPartition = new HashMap<>();
    for (final ZeebeRecord record : records) {
      maxByPartition.merge(record.partitionId(), record.offset(), Math::max);
    }
    maxByPartition.forEach(projectionStore::setConsumedPosition);
  }

  private static void sleep() {
    try {
      Thread.sleep(ERROR_BACKOFF.toMillis());
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    running = false;
    if (thread != null) {
      try {
        thread.join(Duration.ofSeconds(5).toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    processor.close();
  }
}
