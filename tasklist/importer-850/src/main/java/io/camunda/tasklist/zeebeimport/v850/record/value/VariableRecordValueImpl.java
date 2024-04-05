/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.record.value;

import io.camunda.tasklist.zeebeimport.v850.record.RecordValueImpl;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.Objects;

public class VariableRecordValueImpl extends RecordValueImpl implements VariableRecordValue {

  private String name;
  private String value;
  private long scopeKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private String bpmnProcessId;
  private String tenantId;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setScopeKey(long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public VariableRecordValueImpl setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (int) (scopeKey ^ (scopeKey >>> 32));
    result = 31 * result + (int) (processInstanceKey ^ (processInstanceKey >>> 32));
    result = 31 * result + (int) (processDefinitionKey ^ (processDefinitionKey >>> 32));
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
    final VariableRecordValueImpl that = (VariableRecordValueImpl) o;
    return scopeKey == that.scopeKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "VariableRecordValueImpl{"
        + "name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", scopeKey="
        + scopeKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenantId="
        + tenantId
        + "} "
        + super.toString();
  }
}
