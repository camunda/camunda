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
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc.StarterLatencyMetricKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataReadMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DataReadMeter.class);
  private final ScheduledExecutorService executorService;
  private final MeterRegistry registry;
  private final CamundaClient client;
  private final List<ReadQuery> queries;
  private volatile boolean closed;
  private ReadQueryContext queryContext =
      new ReadQueryContext(0L, "", 0L, () -> Pair.of("foo", 0L));

  public DataReadMeter(
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final CamundaClient client,
      final List<ReadQuery> queries) {
    registry = meterRegistry;
    executorService = scheduledExecutorService;
    this.client = client;
    this.queries = queries;
  }

  /**
   * Starts the periodic execution of configured read queries. The next execution of a query is only
   * scheduled once the previous one completed, so there is at most one in-flight execution per
   * query and slow queries neither pile up nor block the scheduler threads.
   */
  public void start() {
    for (final ReadQuery query : queries) {
      final Timer timer =
          MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.READ_BENCHMARK)
              .tag(StarterLatencyMetricKeyNames.QUERY_NAME.asString(), query.name())
              .register(registry);

      scheduleNext(query, timer);
    }
    LOG.info("Started {} read benchmark queries", queries.size());
  }

  private void scheduleNext(final ReadQuery query, final Timer timer) {
    if (closed) {
      return;
    }
    try {
      executorService.schedule(
          () -> executeQuery(query, timer), query.interval().toMillis(), TimeUnit.MILLISECONDS);
    } catch (final RejectedExecutionException e) {
      if (!closed) {
        LOG.error("Failed to schedule read query '{}'", query.name(), e);
      }
    }
  }

  private void executeQuery(final ReadQuery query, final Timer timer) {
    final long startTime = System.nanoTime();
    try {
      query
          .queryFunction()
          .apply(client, queryContext)
          .send()
          // measured on the completing thread, so time spent waiting for the executor is excluded
          .handle((response, error) -> new Result(System.nanoTime() - startTime, error))
          .whenCompleteAsync(
              (result, ignored) -> {
                if (result.error() == null) {
                  timer.record(result.durationNanos(), TimeUnit.NANOSECONDS);
                  LOG.debug(
                      "Read query '{}' executed in {} ms",
                      query.name(),
                      TimeUnit.NANOSECONDS.toMillis(result.durationNanos()));
                } else {
                  LOG.warn("Error while executing read query '{}'", query.name(), result.error());
                }
                scheduleNext(query, timer);
              },
              executorService);
    } catch (final RejectedExecutionException e) {
      if (!closed) {
        LOG.warn("Error while executing read query '{}'", query.name(), e);
      }
    } catch (final Exception e) {
      LOG.warn("Error while executing read query '{}'", query.name(), e);
      scheduleNext(query, timer);
    }
  }

  @Override
  public void close() {
    closed = true;
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

  private record Result(long durationNanos, Throwable error) {}

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
