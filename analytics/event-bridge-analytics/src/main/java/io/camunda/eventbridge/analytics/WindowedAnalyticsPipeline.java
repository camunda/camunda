/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the consumer through one {@link StreamProcessor}: poll the assigned {@code zeebe-records}
 * partitions, fold each record once (the base-projection {@code ProcessExecutionProjector}), fan
 * the derived facts out to the registered rollups, commit the source offset, and flush the rollups'
 * pre-aggregation buffers once per batch (the wall-clock tick).
 *
 * <p>Run multiple instances with the same consumer group and distinct consumer ids: the bridge
 * coordinator assigns source partitions across them and rebalances on membership change. Each fact
 * reaches the serving store once per key per flush; effectively-once comes from advancing the
 * offset only after a flush (a lost in-memory buffer is rebuilt by replay).
 */
public final class WindowedAnalyticsPipeline implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WindowedAnalyticsPipeline.class);
  private static final int MAX_RECORDS = 100;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private final ZeebeRecordConsumer sourceConsumer;
  private final StreamProcessor<ZeebeRecord> processor;

  private volatile boolean running;
  private Thread thread;

  public WindowedAnalyticsPipeline(
      final ZeebeRecordConsumer sourceConsumer, final StreamProcessor<ZeebeRecord> processor) {
    this.sourceConsumer = sourceConsumer;
    this.processor = processor;
  }

  public void start() {
    running = true;
    thread = new Thread(this::run, "analytics-pipeline");
    thread.setDaemon(true);
    thread.start();
  }

  private void run() {
    processor.init();
    while (running) {
      try {
        final List<ZeebeRecord> records = sourceConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        for (final ZeebeRecord record : records) {
          processor.process(record);
          sourceConsumer.commit(record).join();
        }
        if (!records.isEmpty()) {
          processor.flush();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Analytics pipeline poll failed; backing off", e);
        sleep();
      }
    }
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
