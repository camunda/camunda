/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.mutable.MutableProjectionState;

/**
 * Folds element-instance lifecycle into the materialized element row — the sole mutator of the
 * element projection. Activation upserts {@code {start, ACTIVE}} and indexes the row under an
 * event-time straggler deadline; a terminal transition finalizes {@code {end, status, durationMs}};
 * eviction (after the completion fact is derived) drops the row, its deadline and the variables
 * scoped to that element instance.
 */
public final class ElementApplier {

  private final long slaMillis;

  public ElementApplier(final long slaMillis) {
    this.slaMillis = slaMillis;
  }

  public void activate(
      final long elementInstanceKey,
      final long eventTimeMs,
      final boolean isProcess,
      final long parentScopeKey,
      final MutableProjectionState state) {
    state.activateElement(elementInstanceKey, eventTimeMs, isProcess, parentScopeKey);
    state.putDeadline(eventTimeMs + slaMillis, elementInstanceKey);
  }

  public void complete(
      final long elementInstanceKey,
      final long eventTimeMs,
      final ElementStatus status,
      final MutableProjectionState state) {
    state.completeElement(elementInstanceKey, eventTimeMs, status);
  }

  public void evict(
      final long elementInstanceKey, final long startTimeMs, final MutableProjectionState state) {
    state.removeDeadline(startTimeMs + slaMillis, elementInstanceKey);
    state.clearVariables(elementInstanceKey);
    state.evictElement(elementInstanceKey);
  }
}
