/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.CACHED_INSTANCES;
import static io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.JOB_ACTIVATION_TIME;
import static io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.JOB_LIFETIME;
import static io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.PROCESS_INSTANCE_EXECUTION;

import io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.CacheKeyNames;
import io.camunda.zeebe.broker.exporter.metrics.ExecutionLatencyMetricsDoc.CacheType;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutionLatencyMetrics {

  private final AtomicInteger currentCachedInstanceJobsCount = new AtomicInteger();
  private final AtomicInteger currentCacheInstanceProcessInstances = new AtomicInteger();

  private final Timer processInstanceExecutionTime;
  private final Timer jobLifeTime;
  private final Timer jobActivationTime;

  public ExecutionLatencyMetrics() {
    this(new SimpleMeterRegistry());
  }

  public ExecutionLatencyMetrics(final MeterRegistry meterRegistry) {

    processInstanceExecutionTime =
        MicrometerUtil.buildTimer(PROCESS_INSTANCE_EXECUTION).register(meterRegistry);
    jobLifeTime = MicrometerUtil.buildTimer(JOB_LIFETIME).register(meterRegistry);
    jobActivationTime = MicrometerUtil.buildTimer(JOB_ACTIVATION_TIME).register(meterRegistry);
    registerCacheCount(
        currentCacheInstanceProcessInstances, CacheType.PROCESS_INSTANCES, meterRegistry);
    registerCacheCount(currentCachedInstanceJobsCount, CacheType.JOBS, meterRegistry);
  }

  private void registerCacheCount(
      final AtomicInteger state, final CacheType type, final MeterRegistry meterRegistry) {
    Gauge.builder(CACHED_INSTANCES.getName(), state, AtomicInteger::get)
        .description(CACHED_INSTANCES.getDescription())
        .tag(CacheKeyNames.TYPE.asString(), type.getTagValue())
        .register(meterRegistry);
  }

  public void observeProcessInstanceExecutionTime(
      final long creationTimeMs, final long completionTimeMs) {
    processInstanceExecutionTime.record(completionTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void observeJobLifeTime(final long creationTimeMs, final long completionTimeMs) {
    jobLifeTime.record(completionTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void observeJobActivationTime(final long creationTimeMs, final long activationTimeMs) {
    jobActivationTime.record(activationTimeMs - creationTimeMs, TimeUnit.MILLISECONDS);
  }

  public void setCurrentJobsCount(final int count) {
    currentCachedInstanceJobsCount.set(count);
  }

  public void setCurrentProcessInstanceCount(final int count) {
    currentCacheInstanceProcessInstances.set(count);
  }
}
