/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.scheduler.ActorMetricsDoc.*;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class ActorMetricsImpl implements ActorMetrics {
  private final Map<SubscriptionType, Timer> schedulingLatency =
      new EnumMap<>(SubscriptionType.class);
  private final MeterRegistry registry;

  public ActorMetricsImpl(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    for (final SubscriptionType type : SubscriptionType.values()) {
      schedulingLatency.put(type, createSchedulingTimer(type));
    }
  }

  private Timer createSchedulingTimer(final SubscriptionType subscriptionType) {
    return Timer.builder(SCHEDULING_LATENCY.getName())
        .description(SCHEDULING_LATENCY.getDescription())
        .tag(ActorMetricsKeyName.SUBSCRIPTION_TYPE.asString(), subscriptionType.getName())
        .serviceLevelObjectives(SCHEDULING_LATENCY.getTimerSLOs())
        .register(registry);
  }

  private Timer createExecutionTimer(final String actorName) {
    return Timer.builder(EXECUTION_LATENCY.getName())
        .description(EXECUTION_LATENCY.getDescription())
        .tag(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
        .serviceLevelObjectives(EXECUTION_LATENCY.getTimerSLOs())
        .register(registry);
  }

  private Counter createExecutionCount(final String actorName) {
    return Counter.builder(EXECUTION_COUNT.getName())
        .description(EXECUTION_COUNT.getDescription())
        .tag(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
        .register(registry);
  }

  private Gauge createJobQueueLength(final String actorName, final AtomicLong value) {
    return Gauge.builder(JOB_QUEUE_LENGTH.getName(), value::get)
        .description(JOB_QUEUE_LENGTH.getDescription())
        .tag(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
        .register(registry);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public ActorMetricsScoped scoped(final String actorName) {
    final var jobQueueLength = new AtomicLong(0L);
    return new ActorMetricsScopedEnabled(
        registry,
        createExecutionTimer(actorName),
        createJobQueueLength(actorName, jobQueueLength),
        jobQueueLength,
        createExecutionCount(actorName));
  }

  @Override
  public void observeJobSchedulingLatency(
      final long waitTimeNs, final SubscriptionType subscriptionType) {
    schedulingLatency.get(subscriptionType).record(waitTimeNs, TimeUnit.NANOSECONDS);
  }

  record ActorMetricsScopedEnabled(
      MeterRegistry registry,
      Timer executionLatency,
      Gauge jobQueueLengthGauge,
      AtomicLong jobQueueLength,
      Counter executionCount)
      implements ActorMetricsScoped {

    @Override
    public void close() {
      jobQueueLength.set(0);
      registry.remove(executionLatency);
      registry.remove(jobQueueLengthGauge);
      registry.remove(executionCount);
    }

    @Override
    public void countExecution() {
      executionCount.increment();
    }

    @Override
    public void updateJobQueueLength(final int length) {
      jobQueueLength.set(length);
    }

    @Override
    public CloseableSilently startExecutionTimer() {
      return MicrometerUtil.timer(executionLatency, Timer.start(registry));
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
