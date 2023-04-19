/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record;

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

  public void setVariables(Map<String, Object> variables) {
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
  public boolean equals(Object o) {
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
