/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.state.mutable.MutableProjectionState;

/**
 * Folds a {@code VARIABLE} record into the projection: records the latest value under its scope so
 * a later completion of that scope can enrich its fact with the variable snapshot. The sole mutator
 * of the variable store; it derives no fact (a variable only ever surfaces as an enrichment
 * dimension).
 *
 * <p>Overwrite-on-set per {@code (scopeKey, name)}, matching the workflow engine's {@code
 * DbVariableState.setVariableLocal} (an {@code upsert}): each {@code VARIABLE} record is already
 * one atomic {@code (scope, name, value)} — the engine splits variable documents into per-variable
 * records upstream — so there is nothing to merge. Reads resolve up the scope hierarchy like the
 * engine's variable visibility (see {@code ProjectionState#variables}).
 */
public final class VariableApplier {

  public void put(
      final long scopeKey,
      final String name,
      final String value,
      final MutableProjectionState state) {
    state.putVariable(scopeKey, name, unquote(value));
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
