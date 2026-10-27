/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import java.time.Duration;
import java.time.InstantSource;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ProcessingScheduleServiceFactory {
  private final Supplier<Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<LogStreamWriter> writerSupplier;
  private final StageableScheduledCommandCache commandCache;
  private final InstantSource clock;
  private final Duration interval;
  private final ScheduledTaskMetrics metrics;

  public ProcessingScheduleServiceFactory(
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<LogStreamWriter> writerSupplier,
      final StageableScheduledCommandCache commandCache,
      final InstantSource clock,
      final Duration interval,
      final ScheduledTaskMetrics metrics) {
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerSupplier = writerSupplier;
    this.commandCache = commandCache;
    this.clock = clock;
    this.interval = interval;
    this.metrics = metrics;
  }

  public ProcessingScheduleServiceImpl create() {
    return new ProcessingScheduleServiceImpl(
        streamProcessorPhaseSupplier, // this is volatile
        abortCondition,
        writerSupplier,
        commandCache,
        clock,
        interval,
        metrics);
  }
}
