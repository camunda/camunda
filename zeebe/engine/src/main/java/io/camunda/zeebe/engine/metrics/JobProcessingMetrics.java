/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.util.collection.Table;
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

  private final Table<JobAction, String, Counter> jobEvents = Table.simple();
  private final MeterRegistry registry;

  public JobProcessingMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void countJobEvent(final JobAction action, final String type) {
    countJobEvent(action, type, 1);
  }

  public void countJobEvent(final JobAction action, final String type, final int amount) {
    jobEvents.computeIfAbsent(action, type, this::registerJobEventCounter).increment(amount);
  }

  private Counter registerJobEventCounter(final JobAction jobAction, final String jobType) {
    return Counter.builder(EngineMetricsDoc.JOB_EVENTS.getName())
        .description(EngineMetricsDoc.JOB_EVENTS.getDescription())
        .tag(EngineKeyNames.JOB_ACTION.asString(), jobAction.getLabel())
        .tag(EngineKeyNames.JOB_TYPE.asString(), jobType)
        .register(registry);
  }
}
