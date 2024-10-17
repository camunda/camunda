/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;

public class InputVariableEntry extends VariableEntry {

  private Object value;

  protected InputVariableEntry() {}

  public InputVariableEntry(
      final String id, final String name, final VariableType type, final Object value) {
    super(id, name, type);
    this.value = value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InputVariableEntry)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final InputVariableEntry that = (InputVariableEntry) o;
    return Objects.equals(value, that.value);
  }

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }
}
