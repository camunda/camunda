/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

/**
 * Contract every periodic background task in the engine implements.
 *
 * <p>An implementation only describes the actual work: read state, emit follow-up commands or
 * inter-partition sends through {@link Sink}, and tell the runtime when to fire next via the
 * returned {@link Outcome}. Lifecycle, scheduling cadence, yielding, error handling, logging and
 * metrics are provided once by the {@code ManagedScheduledTask} runtime and shared across all
 * implementations.
 */
public interface ScheduledTask {

  /**
   * Stable, kebab-case identifier used as a label for metrics, log MDC, and tracing. Must be unique
   * per partition.
   */
  String name();

  /**
   * Performs one execution. The runtime invokes this in the stream processor's actor thread (or in
   * a configured async actor). Implementations must not retain references to {@link TaskContext} or
   * {@link Sink} beyond the call.
   */
  Outcome run(TaskContext context);
}
