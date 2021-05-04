/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_0.record.value;

import java.util.Map;
import io.camunda.operate.zeebeimport.v1_0.record.RecordValueImpl;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;

public class VariableDocumentRecordImpl extends RecordValueImpl implements VariableDocumentRecordValue {

  private VariableDocumentUpdateSemantic updateSemantics;
  private long scopeKey;
  private Map<String, Object> variables;

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemantics;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setUpdateSemantics(VariableDocumentUpdateSemantic updateSemantics) {
    this.updateSemantics = updateSemantics;
  }

  public void setScopeKey(long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VariableDocumentRecordImpl that = (VariableDocumentRecordImpl) o;

    if (scopeKey != that.scopeKey)
      return false;
    if (updateSemantics != that.updateSemantics)
      return false;
    return variables != null ? variables.equals(that.variables) : that.variables == null;

  }

  @Override
  public int hashCode() {
    int result = updateSemantics != null ? updateSemantics.hashCode() : 0;
    result = 31 * result + (int) (scopeKey ^ (scopeKey >>> 32));
    result = 31 * result + (variables != null ? variables.hashCode() : 0);
    return result;
  }
}
