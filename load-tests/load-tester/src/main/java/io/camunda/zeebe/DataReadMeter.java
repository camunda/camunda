/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.zeebe.StarterLatencyMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataReadMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DataReadMeter.class);
  private final ScheduledExecutorService executorService;
  private final List<ScheduledFuture<?>> scheduledTasks;
  private final MeterRegistry registry;

  public DataReadMeter(
      final MeterRegistry meterRegistry, final ScheduledExecutorService scheduledExecutorService) {
    registry = meterRegistry;
    executorService = scheduledExecutorService;
    scheduledTasks = new ArrayList<>();
  }

  /** Starts the periodic execution of configured read queries. */
  public void start(final CamundaClient client, final List<ReadQuery> queries) {
    for (final ReadQuery query : queries) {
      final Timer timer =
          MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.READ_BENCHMARK)
              .tag(StarterMetricKeyNames.QUERY_NAME.asString(), query.name())
              .register(registry);

      final ScheduledFuture<?> task =
          executorService.scheduleWithFixedDelay(
              () -> {
                try {
                  executeQuery(query, client, timer);
                } catch (final Exception e) {
                  LOG.error("Failed to execute read query '{}'. Will retry...", query.name(), e);
                }
              },
              query.interval().toMillis(),
              query.interval().toMillis(),
              TimeUnit.MILLISECONDS);

      scheduledTasks.add(task);
    }
    LOG.info("Started {} read benchmark queries", queries.size());
  }

  private void executeQuery(final ReadQuery query, final CamundaClient client, final Timer timer) {
    final long startTime = System.nanoTime();
    query
        .queryFunction()
        .apply(client)
        .send()
        .whenComplete(
            (result, error) -> {
              final long durationNanos = System.nanoTime() - startTime;
              timer.record(durationNanos, TimeUnit.NANOSECONDS);

              if (error != null) {
                LOG.error("Error while executing read query '{}'", query.name(), error);
              } else {
                LOG.debug(
                    "Read query '{}' executed in {} ms",
                    query.name(),
                    TimeUnit.NANOSECONDS.toMillis(durationNanos));
              }
            });
  }

  /** Stops the periodic execution of read queries. */
  public void stop() {
    close();
  }

  @Override
  public void close() {
    for (final ScheduledFuture<?> task : scheduledTasks) {
      task.cancel(true);
    }
    scheduledTasks.clear();
    executorService.shutdownNow();
  }

  /**
   * Represents a read query to be executed periodically.
   *
   * @param name the name of the query (used for metrics)
   * @param interval how often the query should be executed
   * @param queryFunction a function that takes a CamundaClient and returns a CompletionStage
   */
  public record ReadQuery(
      String name, Duration interval, Function<CamundaClient, FinalCommandStep<?>> queryFunction) {}
}
