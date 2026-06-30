/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.eventbridge.analytics.aggregate.AggregateDataset;
import io.camunda.eventbridge.analytics.aggregate.DatasetRegistry;
import io.camunda.eventbridge.analytics.aggregate.WindowedExecutionTimeAggregator;
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase-1 single-stage pipeline (DB-as-merge, no shuffle, no fact-topic, no coordinator). Each
 * instance consumes its assigned {@code zeebe-records} partitions (via the bridge consumer group),
 * folds records into the base projection, and on a derived execution-time fact folds it directly
 * into the windowed aggregate — so the headline metric ("N instances completed per definition per
 * window") is maintained with one local DB transaction per fact.
 *
 * <p>Run multiple instances with the <em>same</em> group id and distinct consumer ids: the bridge
 * coordinator assigns source partitions across them and rebalances on membership change.
 * Correctness is from the windowed aggregator's per-source-partition dedup, so a partition handed
 * to another instance (or a redelivery) cannot double-count. The source offset is committed only
 * after the fact is durably aggregated.
 */
public final class WindowedAnalyticsPipeline implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WindowedAnalyticsPipeline.class);
  private static final int MAX_RECORDS = 100;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);
  private static final Duration DATASET_REFRESH = Duration.ofSeconds(5);

  private final ZeebeRecordConsumer sourceConsumer;
  private final ProcessInstanceProjector projector;
  private final WindowedExecutionTimeAggregator aggregator;
  private final DatasetRegistry datasetRegistry;
  // second metric family on the same consumer: the element heatmap, built on the streaming library
  private final StreamProcessor<ZeebeRecord> heatmap;

  private volatile boolean running;
  private Thread thread;
  private List<AggregateDataset> datasets = List.of();
  private long datasetsRefreshedAt;

  public WindowedAnalyticsPipeline(
      final ZeebeRecordConsumer sourceConsumer,
      final ProcessInstanceProjector projector,
      final WindowedExecutionTimeAggregator aggregator,
      final DatasetRegistry datasetRegistry,
      final StreamProcessor<ZeebeRecord> heatmap) {
    this.sourceConsumer = sourceConsumer;
    this.projector = projector;
    this.aggregator = aggregator;
    this.datasetRegistry = datasetRegistry;
    this.heatmap = heatmap;
  }

  public void start() {
    running = true;
    thread = new Thread(this::run, "analytics-windowed");
    thread.setDaemon(true);
    thread.start();
  }

  private void run() {
    heatmap.init();
    while (running) {
      try {
        refreshDatasetsIfDue();
        final List<ZeebeRecord> records = sourceConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        for (final ZeebeRecord record : records) {
          // fold the derived fact into every currently-declared dataset (each with its own window)
          projector.apply(record).ifPresent(fact -> aggregator.apply(fact, datasets));
          // same record also feeds the element-heatmap metric (buffered, flushed per batch)
          heatmap.process(record);
          sourceConsumer.commit(record).join();
        }
        if (!records.isEmpty()) {
          heatmap.flush();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Windowed pipeline poll failed; backing off", e);
        sleep();
      }
    }
  }

  private void refreshDatasetsIfDue() {
    final long now = System.nanoTime();
    if (datasetsRefreshedAt == 0 || now - datasetsRefreshedAt >= DATASET_REFRESH.toNanos()) {
      final List<AggregateDataset> latest = datasetRegistry.datasets();
      if (!latest.equals(datasets)) {
        LOG.info("Aggregating into {} dataset(s): {}", latest.size(), latest);
      }
      datasets = latest;
      datasetsRefreshedAt = now;
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
    heatmap.close(); // final flush of any buffered heatmap partials
  }
}
