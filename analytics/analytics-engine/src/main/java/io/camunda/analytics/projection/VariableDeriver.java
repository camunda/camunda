/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

/**
 * Folds a {@code VARIABLE} record into the read-model: records the latest value under its instance
 * so a later completion can enrich its fact with the variable snapshot. Emits no fact of its own —
 * a variable only ever surfaces as an enrichment dimension on a completion fact.
 */
final class VariableDeriver implements FactDeriver {

  @Override
  public void derive(
      final SourceRecord source, final BaseProjectionStore state, final Collector<Fact> out) {
    if (source.record().getValue() instanceof final VariableRecordValue variable) {
      state.putVariable(
          variable.getProcessInstanceKey(), variable.getName(), unquote(variable.getValue()));
    }
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
