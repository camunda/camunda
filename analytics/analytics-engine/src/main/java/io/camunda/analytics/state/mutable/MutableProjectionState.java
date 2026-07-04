/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state.mutable;

import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.immutable.ProjectionState;
import java.util.function.LongConsumer;

/**
 * The write view of the Model-A base projection and its <em>sole</em> mutator: appliers fold each
 * record into these rows, derivers read them back through {@link ProjectionState}. Every method is
 * a materialization step (upsert / finalize / evict) with no derivation logic, so "who mutates the
 * read-model" is a single, enumerable surface.
 */
public interface MutableProjectionState extends ProjectionState {

  /**
   * Upserts an element row {@code {start, ACTIVE, isProcess}} on activation, recording its parent
   * (flow) scope so a completion can resolve variables up the scope hierarchy.
   */
  void activateElement(
      long elementInstanceKey, long startTimeMs, boolean isProcess, long parentScopeKey);

  /** Finalizes an element row {@code {end, status, durationMs}} on a terminal transition. */
  void completeElement(long elementInstanceKey, long endTimeMs, ElementStatus status);

  /** Stamps {@code hadIncident} on the element instance's row the incident occurred on. */
  void markIncident(long elementInstanceKey);

  /** Drops an element row (evict-after-emit). */
  void evictElement(long elementInstanceKey);

  /** Puts one scoped variable ({@code (scopeKey, name) -> value}). */
  void putVariable(long scopeKey, String name, String value);

  /** Drops every variable scoped to an element instance (on its terminal transition). */
  void clearVariables(long scopeKey);

  /** Opens an incident row {@code {createMs, errorType}}. */
  void openIncident(long elementInstanceKey, long createMs, String errorType);

  /** Finalizes an incident row with {@code resolveMs} on resolution. */
  void resolveIncident(long elementInstanceKey, long resolveMs);

  /** Drops an incident row (evict-after-emit). */
  void evictIncident(long elementInstanceKey);

  /**
   * Indexes {@code elementInstanceKey} under an event-time {@code deadlineMs} for straggler sweep.
   */
  void putDeadline(long deadlineMs, long elementInstanceKey);

  /** Removes a deadline index entry (on completion, so only in-flight rows are indexed). */
  void removeDeadline(long deadlineMs, long elementInstanceKey);

  /**
   * Evicts every element (and its variables) whose event-time deadline is at or before {@code
   * streamTimeMs}, bounding the materialized projection for instances that never complete. The
   * evicted element instance keys are reported to {@code onExpired}.
   */
  void sweepExpiredDeadlines(long streamTimeMs, LongConsumer onExpired);

  /** Makes all working state durable inside the runtime's checkpoint transaction. */
  void checkpoint();

  /** Whether a bounded cache is full and the runtime should checkpoint early. */
  boolean needsCheckpoint();
}
