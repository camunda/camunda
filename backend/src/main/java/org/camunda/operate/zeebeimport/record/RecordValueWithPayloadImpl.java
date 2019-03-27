/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.exporter.api.record.RecordValueWithVariables;

public abstract class RecordValueWithPayloadImpl implements RecordValue, RecordValueWithVariables {
  private String variables;

  public RecordValueWithPayloadImpl() {
  }

  @Override
  public String getVariables() {
    return variables;
  }

  public void setVariables(String variables) {
    this.variables = variables;
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getVariablesAsMap() {
    throw new UnsupportedOperationException("getVariablesAsMap operation is not supported");
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    RecordValueWithPayloadImpl that = (RecordValueWithPayloadImpl) o;

    return variables != null ? variables.equals(that.variables) : that.variables == null;
  }

  @Override
  public int hashCode() {
    return variables != null ? variables.hashCode() : 0;
  }
}
