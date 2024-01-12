/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.record.value;

import io.camunda.tasklist.zeebeimport.v850.record.RecordValueImpl;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.util.Map;
import java.util.Objects;

public class VariableDocumentRecordImpl extends RecordValueImpl
    implements VariableDocumentRecordValue {

  private VariableDocumentUpdateSemantic updateSemantics;
  private long scopeKey;
  private Map<String, Object> variables;

  private String tenantId;

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemantics;
  }

  public void setUpdateSemantics(VariableDocumentUpdateSemantic updateSemantics) {
    this.updateSemantics = updateSemantics;
  }

  public void setScopeKey(long scopeKey) {
    this.scopeKey = scopeKey;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public int hashCode() {
    int result = updateSemantics != null ? updateSemantics.hashCode() : 0;
    result = 31 * result + (int) (scopeKey ^ (scopeKey >>> 32));
    result = 31 * result + (variables != null ? variables.hashCode() : 0);
    result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableDocumentRecordImpl that = (VariableDocumentRecordImpl) o;
    return scopeKey == that.scopeKey
        && updateSemantics == that.updateSemantics
        && Objects.equals(variables, that.variables)
        && Objects.equals(tenantId, that.tenantId);
  }
}
