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
import org.agrona.DirectBuffer;

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
  default void activateElement(
      final long elementInstanceKey,
      final long startTimeMs,
      final boolean isProcess,
      final long parentScopeKey) {
    activateElement(elementInstanceKey, startTimeMs, isProcess, parentScopeKey, null);
  }

  /**
   * Upserts an element row on activation, additionally materializing the instance's activation-time
   * business value ({@code null} when absent or non-numeric — process rows only). The end fact
   * subtracts exactly this materialized value, so the value-in-flight level stays balanced even
   * when the variable is created or changed mid-flight.
   */
  void activateElement(
      long elementInstanceKey,
      long startTimeMs,
      boolean isProcess,
      long parentScopeKey,
      Long businessValue);

  /**
   * Finalizes an element row {@code {end, status, durationMs}} on a terminal transition.
   *
   * @return {@code false} when no row existed to finalize (no activation was folded) — the caller
   *     surfaces the miss; the state itself stays a plain materialization step
   */
  boolean completeElement(long elementInstanceKey, long endTimeMs, ElementStatus status);

  /**
   * Stamps {@code hadIncident} on the element instance's row the incident occurred on.
   *
   * @return {@code false} when no row existed to stamp (no activation was folded)
   */
  boolean markIncident(long elementInstanceKey);

  /** Drops an element row (evict-after-emit). */
  void evictElement(long elementInstanceKey);

  /**
   * Puts one scoped variable ({@code (scopeKey, name) -> value}). Name and value arrive as UTF-8
   * views over the source record's bytes (ADR 0008) and are copied into the store — the buffers are
   * only borrowed for the call.
   */
  void putVariable(long scopeKey, DirectBuffer name, DirectBuffer value);

  /** Drops every variable scoped to an element instance (on its terminal transition). */
  void clearVariables(long scopeKey);

  /**
   * Bumps the instance's variant accumulator for one executed element ({@code (processInstanceKey,
   * elementId) -> count + 1}). The element id arrives as a UTF-8 view over the source record's
   * bytes and is copied into the store key — the buffer is only borrowed for the call.
   */
  void countVariantElement(long processInstanceKey, DirectBuffer elementId);

  /** Drops the instance's whole variant accumulator (when the instance's row is evicted). */
  void clearVariantElements(long processInstanceKey);

  /** Opens an incident row {@code {createMs, errorType}}. */
  void openIncident(long elementInstanceKey, long createMs, String errorType);

  /** Finalizes an incident row with {@code resolveMs} on resolution. */
  void resolveIncident(long elementInstanceKey, long resolveMs);

  /** Drops an incident row (evict-after-emit). */
  void evictIncident(long elementInstanceKey);

  /** Makes all working state durable inside the runtime's checkpoint transaction. */
  void checkpoint();

  /** Whether a bounded cache is full and the runtime should checkpoint early. */
  boolean needsCheckpoint();
}
