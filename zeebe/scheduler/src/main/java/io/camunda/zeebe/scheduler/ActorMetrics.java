/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ActorMetrics {
  private final Map<SubscriptionType, Timer> schedulingLatency =
      new EnumMap<>(SubscriptionType.class);
  private final MeterRegistry registry;

  public ActorMetrics(final MeterRegistry registry) {
    this.registry = registry;
    if (isEnabled()) {
      for (final SubscriptionType type : SubscriptionType.values()) {
        schedulingLatency.put(type, createSchedulingTimer(type));
      }
    }
  }

  public static ActorMetrics disabled() {
    return new ActorMetrics(null);
  }

  public ActorMetricsScoped scoped(final String actorName) {
    final var jobQueueLength = new AtomicLong(0L);
    if (registry != null) {
      return new ActorMetricsScopedEnabled(
          registry,
          createExecutionTimer(actorName),
          createJobQueueLength(actorName, jobQueueLength),
          jobQueueLength,
          createExecutionCount(actorName));
    } else {
      return ActorMetricsScoped.noop();
    }
  }

  public void observeJobSchedulingLatency(
      final long waitTimeNs, final SubscriptionType subscriptionType) {
    if (isEnabled()) {
      schedulingLatency.get(subscriptionType).record(waitTimeNs, TimeUnit.NANOSECONDS);
    }
  }

  private Timer createSchedulingTimer(final SubscriptionType subscriptionType) {
    return Timer.builder(SCHEDULING_LATENCY.getName())
        .description(SCHEDULING_LATENCY.getDescription())
        .tags(ActorMetricsKeyName.SUBSCRIPTION_TYPE.asString(), subscriptionType.getName())
        .serviceLevelObjectives(SCHEDULING_LATENCY.getTimerSLOs())
        .register(registry);
  }

  private Timer createExecutionTimer(final String actorName) {
    return Timer.builder(EXECUTION_LATENCY.getName())
        .description(EXECUTION_LATENCY.getDescription())
        .tags(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
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
        .tags(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
        .register(registry);
  }

  public boolean isEnabled() {
    return registry != null;
  }

  public record ActorMetricsScopedEnabled(
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
  }

  public interface ActorMetricsScoped extends CloseableSilently {
    ActorMetricsScoped NOOP =
        new ActorMetricsScoped() {

          @Override
          public void close() {}

          @Override
          public void countExecution() {}

          @Override
          public void updateJobQueueLength(final int length) {}

          @Override
          public CloseableSilently startExecutionTimer() {
            return () -> {};
          }
        };

    void countExecution();

    void updateJobQueueLength(final int length);

    CloseableSilently startExecutionTimer();

    static ActorMetricsScoped noop() {
      return NOOP;
    }
  }

  public enum SubscriptionType {
    FUTURE("Future"),
    TIMER("Timer"),
    NONE("None");

    private final String name;

    SubscriptionType(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
