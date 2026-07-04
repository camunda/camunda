/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

/**
 * Records a variable's latest value under its scope so a later completion of that scope can enrich
 * its fact. Overwrite-on-set per {@code (scopeKey, name)}, matching the engine's {@code
 * DbVariableState.setVariableLocal}: each {@code VARIABLE} record is already one atomic {@code
 * (scope, name, value)}, so there is nothing to merge. Emits no fact.
 */
public final class VariableApplier implements EventApplier {

  private final MutableProjectionState state;

  public VariableApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final VariableRecordValue value = (VariableRecordValue) source.record().getValue();
    state.putVariable(value.getScopeKey(), value.getName(), unquote(value.getValue()));
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
