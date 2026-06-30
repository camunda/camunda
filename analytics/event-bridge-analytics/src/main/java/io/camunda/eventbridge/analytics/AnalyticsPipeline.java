/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.eventbridge.analytics.aggregate.ExecutionTimeAggregator;
import io.camunda.eventbridge.analytics.fact.FactSink;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFactCodec;
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires the three stages into a running pipeline:
 *
 * <ol>
 *   <li>Stage 1 polls the source ({@code zeebe-records}) consumer, folds each record into the base
 *       projection, and publishes any derived fact to the {@link FactSink}; the source offset is
 *       committed only after the fact is published (publish-then-advance).
 *   <li>Stage 3 polls the fact topic, deserializes each fact, and folds it into the aggregated
 *       dataset; the aggregator dedups, so committing the fact offset after the fold is safe.
 * </ol>
 *
 * <p>Each stage runs on its own daemon thread; {@link #close()} stops them.
 */
public final class AnalyticsPipeline implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsPipeline.class);
  private static final int MAX_RECORDS = 100;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private final ZeebeRecordConsumer sourceConsumer;
  private final ProcessInstanceProjector projector;
  private final FactSink factSink;

  private final Consumer factConsumer;
  private final ProcessInstanceExecutionTimeFactCodec codec =
      new ProcessInstanceExecutionTimeFactCodec();
  private final ExecutionTimeAggregator aggregator;

  private volatile boolean running;
  private Thread projectionThread;
  private Thread aggregationThread;

  public AnalyticsPipeline(
      final ZeebeRecordConsumer sourceConsumer,
      final ProcessInstanceProjector projector,
      final FactSink factSink,
      final Consumer factConsumer,
      final ExecutionTimeAggregator aggregator) {
    this.sourceConsumer = sourceConsumer;
    this.projector = projector;
    this.factSink = factSink;
    this.factConsumer = factConsumer;
    this.aggregator = aggregator;
  }

  public void start() {
    running = true;
    projectionThread = daemon("analytics-projection", this::runProjection);
    aggregationThread = daemon("analytics-aggregation", this::runAggregation);
  }

  private void runProjection() {
    while (running) {
      try {
        final List<ZeebeRecord> records = sourceConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        for (final ZeebeRecord record : records) {
          projector.apply(record, factSink::publish);
          sourceConsumer.commit(record).join();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Projection stage poll failed; backing off", e);
        sleep();
      }
    }
  }

  private void runAggregation() {
    while (running) {
      try {
        final List<Event> events = factConsumer.poll(MAX_RECORDS, POLL_TIMEOUT);
        for (final Event event : events) {
          aggregator.apply(codec.deserialize(event.payload()));
          factConsumer.commitOffset(event.topic(), event.partitionId(), event.position()).join();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Aggregation stage poll failed; backing off", e);
        sleep();
      }
    }
  }

  private static Thread daemon(final String name, final Runnable body) {
    final Thread thread = new Thread(body, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
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
    join(projectionThread);
    join(aggregationThread);
  }

  private static void join(final Thread thread) {
    if (thread != null) {
      try {
        thread.join(Duration.ofSeconds(5).toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
