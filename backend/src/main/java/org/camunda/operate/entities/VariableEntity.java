/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

public class VariableEntity extends OperateZeebeEntity {

  private String name;
  private String value;
  private Long scopeKey;
  private Long workflowInstanceKey;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    VariableEntity that = (VariableEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (value != null ? !value.equals(that.value) : that.value != null)
      return false;
    if (scopeKey != null ? !scopeKey.equals(that.scopeKey) : that.scopeKey != null)
      return false;
    return workflowInstanceKey != null ? workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (scopeKey != null ? scopeKey.hashCode() : 0);
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    return result;
  }
}
