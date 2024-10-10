/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ExecutionLatencyMetrics {

  private static final Duration[] JOB_LIFE_TIME_BUCKETS =
      Stream.of(25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 10000, 15000, 30000, 45000)
          .map(Duration::ofMillis)
          .toArray(Duration[]::new);

  private static final Duration[] JOB_ACTIVATION_TIME_BUCKETS =
      Stream.of(10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 10000, 15000, 30000)
          .map(Duration::ofMillis)
          .toArray(Duration[]::new);

  private static final Duration[] PROCESS_INSTANCE_EXECUTION_BUCKETS =
      Stream.of(50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 10000, 15000, 30000, 45000, 60000)
          .map(Duration::ofMillis)
          .toArray(Duration[]::new);

  private final MeterRegistry meterRegistry;
  private final Map<Integer, AtomicInteger> currentCachedInstanceJobsCount =
      new ConcurrentHashMap<>();
  private final Map<Integer, AtomicInteger> currentCacheInstanceProcessInstances =
      new ConcurrentHashMap<>();
  private final int partitionId;

  public ExecutionLatencyMetrics() {
    this(new SimpleMeterRegistry(), 1);
  }

  public ExecutionLatencyMetrics(final MeterRegistry meterRegistry, final int partitionId) {
    this.meterRegistry = meterRegistry;
    this.partitionId = partitionId;
  }

  public void observeProcessInstanceExecutionTime(
      final long creationTimeMs, final long completionTimeMs) {
    Timer.builder("zeebe.process.instance.execution.time")
        .description("The execution time of processing a complete process instance")
        .sla(PROCESS_INSTANCE_EXECUTION_BUCKETS)
        .register(meterRegistry)
        .record(completionTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void observeJobLifeTime(final long creationTimeMs, final long completionTimeMs) {
    Timer.builder("zeebe.job.life.time")
        .description("The life time of an job")
        .sla(JOB_LIFE_TIME_BUCKETS)
        .register(meterRegistry)
        .record(completionTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void observeJobActivationTime(final long creationTimeMs, final long activationTimeMs) {
    Timer.builder("zeebe.job.activation.time")
        .description("The time until an job was activated")
        .sla(JOB_ACTIVATION_TIME_BUCKETS)
        .register(meterRegistry)
        .record(activationTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void setCurrentJobsCount(final int count) {
    setCurrentCachedInstanceGauge(count, "jobs");
  }

  public void setCurrentProcessInstanceCount(final int count) {
    setCurrentCachedInstanceGauge(count, "processInstances");
  }

  private void setCurrentCachedInstanceGauge(final int count, final String type) {
    final var collection =
        "jobs".equals(type) ? currentCachedInstanceJobsCount : currentCacheInstanceProcessInstances;

    collection.putIfAbsent(partitionId, new AtomicInteger());
    collection.get(partitionId).set(count);

    Gauge.builder(
            "zeebe.execution.latency.current.cached.instances",
            () -> collection.get(partitionId).get())
        .description(
            "The current cached instances for counting their execution latency. If only short-lived instances are handled this can be seen or observed as the current active instance count.")
        .tags("type", type)
        .register(meterRegistry);
  }
}
