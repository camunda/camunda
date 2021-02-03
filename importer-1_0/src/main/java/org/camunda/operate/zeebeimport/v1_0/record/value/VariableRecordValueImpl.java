/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.record.value;

import org.camunda.operate.zeebeimport.v1_0.record.RecordValueImpl;
import io.zeebe.protocol.record.value.VariableRecordValue;

public class VariableRecordValueImpl extends RecordValueImpl implements VariableRecordValue {

  private String name;
  private String value;
  private long scopeKey;
  private long workflowInstanceKey;
  private long workflowKey;

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
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setScopeKey(long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VariableRecordValueImpl that = (VariableRecordValueImpl) o;

    if (scopeKey != that.scopeKey)
      return false;
    if (workflowInstanceKey != that.workflowInstanceKey)
      return false;
    if (workflowKey != that.workflowKey)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (int) (scopeKey ^ (scopeKey >>> 32));
    result = 31 * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
    result = 31 * result + (int) (workflowKey ^ (workflowKey >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "VariableRecordValueImpl{" + "name='" + name + '\'' + ", value='" + value + '\'' + ", scopeKey=" + scopeKey + ", workflowInstanceKey="
      + workflowInstanceKey + ", workflowKey=" + workflowKey + "} " + super.toString();
  }
}
