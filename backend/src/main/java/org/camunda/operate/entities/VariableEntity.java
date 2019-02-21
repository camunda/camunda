/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

public class VariableEntity extends OperateZeebeEntity {

  private String name;
  private String value;
  private String scopeId;
  private String workflowInstanceId;

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

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
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
    if (scopeId != null ? !scopeId.equals(that.scopeId) : that.scopeId != null)
      return false;
    return workflowInstanceId != null ? workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (scopeId != null ? scopeId.hashCode() : 0);
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    return result;
  }
}
