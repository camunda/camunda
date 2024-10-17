/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import java.util.Map;
import java.util.Objects;

public abstract class RecordValueWithPayloadImpl implements RecordValue, RecordValueWithVariables {

  private Map<String, Object> variables;

  public RecordValueWithPayloadImpl() {}

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public int hashCode() {
    return variables != null ? variables.hashCode() : 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final RecordValueWithPayloadImpl that = (RecordValueWithPayloadImpl) o;

    return Objects.equals(variables, that.variables);
  }
}
