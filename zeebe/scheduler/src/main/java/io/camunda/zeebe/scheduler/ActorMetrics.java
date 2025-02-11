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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorMetrics {
  private static final Logger LOG = LoggerFactory.getLogger(ActorMetrics.class);

  // Map subscriptionType -> Timer
  private final Map<String, Timer> schedulingLatency = new ConcurrentHashMap<>();
  // Lock to be held when modifying schedulingLatency
  private final ReentrantLock schedulingLatencyLock = new ReentrantLock();
  private final MeterRegistry registry;

  public ActorMetrics(final MeterRegistry registry) {
    this.registry = registry;
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
          createSchedulingTimer(actorName),
          createJobQueueLength(actorName, jobQueueLength),
          jobQueueLength,
          createExecutionCount(actorName));
    } else {
      return ActorMetricsScoped.noop();
    }
  }

  private Timer createSchedulingTimer(final String subscriptionType) {
    return Timer.builder(SCHEDULING_LATENCY.getName())
        .description(SCHEDULING_LATENCY.getDescription())
        .tags(ActorMetricsKeyName.SUBSCRIPTION_TYPE.asString(), subscriptionType)
        // FIXME exponential buckets
        .register(registry);
  }

  private Timer createExecutionTimer(final String actorName) {
    return Timer.builder(EXECUTION_LATENCY.getName())
        .description(EXECUTION_LATENCY.getDescription())
        .tags(ActorMetricsKeyName.ACTOR_NAME.asString(), actorName)
        // FIXME
        .serviceLevelObjectives(EXECUTION_LATENCY.getTimerSLOs())
        // exponential buckets
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

  public void observeJobSchedulingLatency(final long waitTimeNs, final String subscriptionType) {
    if (isEnabled()) {
      final var timer = getSchedulingLatency(subscriptionType);
      if (timer != null) {
        timer.record(waitTimeNs, TimeUnit.NANOSECONDS);
      }
    }
  }

  private Timer getSchedulingLatency(final String subscriptionType) {
    var timer = schedulingLatency.get(subscriptionType);
    if (timer == null) {
      try {
        final var acquired = schedulingLatencyLock.tryLock(100, TimeUnit.MILLISECONDS);
        if (!acquired) {
          LOG.warn(
              "Unable to acquire log to register scheduling latency within 100 milliseconds for subscriptionType {}",
              subscriptionType);
          return null;
        }
        // check if it's not already registered while we were taking the lock
        if (!schedulingLatency.containsKey(subscriptionType)) {
          timer = createSchedulingTimer(subscriptionType);
          schedulingLatency.put(subscriptionType, timer);
        }
      } catch (final InterruptedException ex) {
        LOG.warn("Unable to acquire log to register scheduling latency, interrupted", ex);
      } finally {
        schedulingLatencyLock.unlock();
      }
    }
    return timer;
  }

  public boolean isEnabled() {
    return registry != null;
  }

  public record ActorMetricsScopedEnabled(
      MeterRegistry registry,
      Timer executionLatency,
      Timer schedulingLatency,
      Gauge jobQueueLengthGauge,
      AtomicLong jobQueueLength,
      Counter executionCount)
      implements ActorMetricsScoped {

    @Override
    public void close() {
      jobQueueLength.set(0);
      registry.remove(executionLatency);
      registry.remove(schedulingLatency);
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
}
