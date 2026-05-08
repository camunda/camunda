/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class PersistedVariableNames extends UnpackedObject implements DbValue {

  private static final StringValue VARIABLE_NAMES = new StringValue("variableNames");

  private final ArrayProperty<StringValue> variableNamesProperty =
      new ArrayProperty<>(VARIABLE_NAMES, StringValue::new);

  public PersistedVariableNames() {
    super(0);
    declareProperty(variableNamesProperty);
  }

  public PersistedVariableNames addVariableName(final DirectBuffer variableName) {
    if (!contains(variableName)) {
      variableNamesProperty.add().wrap(BufferUtil.cloneBuffer(variableName));
    }
    return this;
  }

  public boolean contains(final DirectBuffer variableName) {
    return variableNamesProperty.stream()
        .anyMatch(name -> BufferUtil.equals(name.getValue(), variableName));
  }

  public List<DirectBuffer> getVariableNames() {
    return variableNamesProperty.stream()
        .map(name -> BufferUtil.cloneBuffer(name.getValue()))
        .toList();
  }
}
