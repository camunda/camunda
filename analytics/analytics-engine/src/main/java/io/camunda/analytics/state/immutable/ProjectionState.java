/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state.immutable;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.state.ElementEntity;
import io.camunda.analytics.state.IncidentEntity;
import io.camunda.analytics.state.VariableNames;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * The read view of the Model-A base projection: the materialized rows a deriver reads to build a
 * fact. Derivers depend only on this interface, so they cannot mutate the projection — mutation is
 * the appliers' sole responsibility (see {@code MutableProjectionState}).
 */
public interface ProjectionState {

  /** The element instance's materialized row, or {@code null} if none is materialized. */
  ElementEntity element(long elementInstanceKey);

  /** The open incident's materialized row, or {@code null} if none is materialized. */
  IncidentEntity incident(long elementInstanceKey);

  /**
   * The variable snapshot visible to an element instance, resolved up the scope hierarchy from its
   * local scope to the process-instance scope (a nearer scope's value wins), like the engine's
   * variable visibility. Empty if none. Values are owned UTF-8 views — never decoded to {@code
   * String} on this path (ADR 0008).
   */
  Map<String, Utf8View> variables(long scopeKey);

  /**
   * Like {@link #variables(long)} but resolves ONLY the named variables — via point lookups up the
   * scope hierarchy that stop as soon as every requested name is found, rather than scanning the
   * whole scope (the engine's fetch-only-needed-names read). Empty if {@code names} is empty. The
   * enrichment path passes the union of {@code var.*} names any dataset groups or filters by (their
   * UTF-8 bytes precomputed once per topology), so a fact only pays to resolve the variables some
   * meter actually reads — and gets them as value slices, not {@code String}s.
   */
  Map<String, Utf8View> variables(long scopeKey, VariableNames names);

  /**
   * Visits the instance's variant accumulator — each distinct executed element id with its
   * activation count, in store (element-id byte) order. Nothing is visited for an instance that
   * activated no elements (or was already cleared). Read once per instance end, when the variant
   * signature is derived.
   */
  void forEachVariantElement(long processInstanceKey, ObjLongConsumer<String> visitor);
}
