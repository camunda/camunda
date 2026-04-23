/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.read;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.zeebe.StarterLatencyMetricsDoc;
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
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataReadMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DataReadMeter.class);
  private final ScheduledExecutorService executorService;
  private final List<ScheduledFuture<?>> scheduledTasks;
  private final MeterRegistry registry;
  private final CamundaClient client;
  private final List<ReadQuery> queries;
  private ReadQueryContext queryContext =
      new ReadQueryContext(0L, "", 0L, () -> Pair.of("foo", 0L));

  public DataReadMeter(
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final CamundaClient client,
      final List<ReadQuery> queries) {
    registry = meterRegistry;
    executorService = scheduledExecutorService;
    scheduledTasks = new ArrayList<>();
    this.client = client;
    this.queries = queries;
  }

  /** Starts the periodic execution of configured read queries. */
  public void start() {
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
    try {
      // to limit parallel executions, we limit the scheduling frequency and execute synchronously
      // this might lead to less data points if a query execution takes longer than the scheduling
      // interval, but it prevents piling up of executions and thus influencing the actual load-test
      query.queryFunction().apply(client, queryContext).send().join();
      final long durationNanos = System.nanoTime() - startTime;
      timer.record(durationNanos, TimeUnit.NANOSECONDS);
      LOG.debug(
          "Read query '{}' executed in {} ms",
          query.name(),
          TimeUnit.NANOSECONDS.toMillis(durationNanos));

    } catch (final Exception e) {
      LOG.warn("Error while executing read query '{}'", query.name(), e);
    }
  }

  @Override
  public void close() {
    for (final ScheduledFuture<?> task : scheduledTasks) {
      task.cancel(true);
    }
    scheduledTasks.clear();
    executorService.shutdownNow();
  }

  public void setContextProcessInstanceKey(final long processInstanceKey) {
    queryContext = queryContext.withProcessInstanceKey(processInstanceKey);
  }

  public void setContextProcessDefinitionId(final String processDefinitionId) {
    queryContext = queryContext.withBenchmarkProcessDefinitionId(processDefinitionId);
  }

  public void setContextProcessDefinitionKey(final long processDefinitionKey) {
    queryContext = queryContext.withBenchmarkProcessDefinitionKey(processDefinitionKey);
  }

  public void setContextBusinessKeySupplier(
      final Supplier<Pair<String, Object>> businessKeySupplier) {
    queryContext = queryContext.withBusinessKey(businessKeySupplier);
  }

  /**
   * Represents a read query to be executed periodically.
   *
   * @param name the name of the query (used for metrics)
   * @param interval how often the query should be executed
   * @param queryFunction a function that takes a CamundaClient and returns a CompletionStage
   */
  public record ReadQuery(
      String name,
      Duration interval,
      BiFunction<CamundaClient, ReadQueryContext, FinalCommandStep<?>> queryFunction) {}

  public record ReadQueryContext(
      long processInstanceKey,
      String benchmarkProcessDefinitionId,
      long benchmarkProcessDefinitionKey,
      Supplier<Pair<String, Object>> businessKeySupplier) {
    public ReadQueryContext withProcessInstanceKey(final long processInstanceKey) {
      return new ReadQueryContext(
          processInstanceKey,
          benchmarkProcessDefinitionId,
          benchmarkProcessDefinitionKey,
          businessKeySupplier);
    }

    public ReadQueryContext withBenchmarkProcessDefinitionId(
        final String benchmarkProcessDefinitionId) {
      return new ReadQueryContext(
          processInstanceKey,
          benchmarkProcessDefinitionId,
          benchmarkProcessDefinitionKey,
          businessKeySupplier);
    }

    public ReadQueryContext withBenchmarkProcessDefinitionKey(final long processDefinitionKey) {
      return new ReadQueryContext(
          processInstanceKey,
          benchmarkProcessDefinitionId,
          processDefinitionKey,
          businessKeySupplier);
    }

    public ReadQueryContext withBusinessKey(
        final Supplier<Pair<String, Object>> businessKeySupplier) {
      return new ReadQueryContext(
          processInstanceKey,
          benchmarkProcessDefinitionId,
          benchmarkProcessDefinitionKey,
          businessKeySupplier);
    }
  }
}
