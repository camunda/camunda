/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JOB_EVENTS;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.BoundedMeterCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.jcip.annotations.NotThreadSafe;

/**
 * This class is not thread safe, and is meant to be used only in the context of the engine actor.
 *
 * <p>If you need to use it from the scheduling actor, or from somewhere else, then you need to
 * first make this thread safe.
 */
@NotThreadSafe
public final class JobProcessingMetrics {

  private final Table<JobAction, JobKind, BoundedMeterCache<Counter>> jobEvents = Table.simple();
  private final MeterRegistry registry;

  public JobProcessingMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void countJobEvent(final JobAction action, final JobKind kind, final String type) {
    countJobEvent(action, kind, type, 1);
  }

  public void countJobEvent(
      final JobAction action, final JobKind kind, final String type, final int amount) {
    jobEvents
        .computeIfAbsent(action, kind, this::registerJobEventCounter)
        .get(type)
        .increment(amount);
  }

  private BoundedMeterCache<Counter> registerJobEventCounter(
      final JobAction jobAction, final JobKind kind) {
    final var provider =
        Counter.builder(JOB_EVENTS.getName())
            .description(JOB_EVENTS.getDescription())
            .tag(EngineKeyNames.JOB_ACTION.asString(), jobAction.toString())
            .tag(EngineKeyNames.JOB_KIND.asString(), kind.name())
            .withRegistry(registry);

    return BoundedMeterCache.of(registry, provider, EngineKeyNames.JOB_TYPE);
  }
}
