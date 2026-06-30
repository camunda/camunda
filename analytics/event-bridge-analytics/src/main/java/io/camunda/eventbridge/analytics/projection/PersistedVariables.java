/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The msgpack {@link DbValue} holding one process instance's variables (name → value), keyed in
 * RocksDB by {@code processInstanceKey}. This is all the base projection needs to keep per instance
 * now that start times live in the shared element-start store (a process instance is just another
 * element). Variables are accumulated from {@code VARIABLE} records so a derived fact can be
 * grouped or filtered by a variable such as {@code region}. {@link #wrap} resets first, so a reused
 * instance does not accumulate stale entries.
 */
public final class PersistedVariables extends UnpackedObject implements DbValue {

  private final ArrayProperty<PersistedVariable> variablesProp =
      new ArrayProperty<>("variables", PersistedVariable::new);

  public PersistedVariables() {
    super(1);
    declareProperty(variablesProp);
  }

  public PersistedVariables wrap(final Map<String, String> variables) {
    reset();
    variables.forEach((name, value) -> variablesProp.add().set(name, value));
    return this;
  }

  /** The variables as a fresh mutable map (insertion order preserved). */
  public Map<String, String> toMap() {
    final Map<String, String> variables = new LinkedHashMap<>();
    for (final PersistedVariable variable : variablesProp) {
      variables.put(variable.name(), variable.value());
    }
    return variables;
  }
}
