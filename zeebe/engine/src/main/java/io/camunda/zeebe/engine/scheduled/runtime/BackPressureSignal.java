/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

/**
 * Reports whether the partition is currently under back-pressure.
 *
 * <p>Phase 3 decision (2026-05-15): FALLBACK path chosen. Wiring the runtime to the broker's {@code
 * FlowControl} would require additions in both {@code zeebe/logstreams} (a new read-only query on
 * {@code FlowControl}; today only mutating calls like {@code tryAcquire} and config getters such as
 * {@code getRequestLimit} are exposed, and "under back-pressure" has no defined snapshot semantics)
 * and {@code zeebe/stream-platform} (a new accessor on {@code
 * ReadonlyStreamProcessorContext}/{@code StreamProcessorContext} to forward from the held {@code
 * LogStream}). Both modules sit outside {@code zeebe/engine} and the spec explicitly scopes that
 * out of this iteration. Per-task write+processed callbacks stay local to the engine and give us
 * the back-pressure gate we need for #8991 without cross-module API surface.
 */
@FunctionalInterface
public interface BackPressureSignal {

  boolean isUnderBackPressure();

  /** Always returns {@code false}. Use for tests and as a default until wired. */
  static BackPressureSignal alwaysGreen() {
    return () -> false;
  }
}
