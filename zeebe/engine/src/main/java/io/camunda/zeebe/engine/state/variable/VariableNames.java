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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

/** Stores the complete set of local variable names for a scope. */
public final class VariableNames extends UnpackedObject implements DbValue {

  private static final StringValue NAMES = new StringValue("names");

  private final BooleanProperty completeProperty = new BooleanProperty("complete", true);
  private final ArrayProperty<StringValue> variableNamesProperty =
      new ArrayProperty<>(NAMES, StringValue::new);

  public VariableNames() {
    super(1);
    declareProperty(completeProperty);
    declareProperty(variableNamesProperty);
  }

  public void addVariableName(final DirectBuffer variableName) {
    if (containsVariableName(variableName)) {
      return;
    }

    variableNamesProperty.add().wrap(BufferUtil.cloneBuffer(variableName));
  }

  public List<DirectBuffer> getVariableNames() {
    return variableNamesProperty.stream()
        .map(name -> BufferUtil.cloneBuffer(name.getValue()))
        .toList();
  }

  public boolean isComplete() {
    return completeProperty.getValue();
  }

  public void markComplete() {
    completeProperty.setValue(true);
  }

  public void markIncomplete() {
    completeProperty.setValue(false);
  }

  public void clear() {
    variableNamesProperty.reset();
  }

  private boolean containsVariableName(final DirectBuffer variableName) {
    return variableNamesProperty.stream()
        .anyMatch(existingName -> BufferUtil.equals(existingName.getValue(), variableName));
  }

  @Override
  public void copyTo(final DbValue target) {
    super.copyTo((VariableNames) target);
  }

  @Override
  public VariableNames newInstance() {
    return new VariableNames();
  }
}
