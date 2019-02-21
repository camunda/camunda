/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.detailview;

public class VariablesRequestDto {

  private String workflowInstanceId;

  private String scopeId;

  public VariablesRequestDto() {
  }

  public VariablesRequestDto(String workflowInstanceId, String scopeId) {
    this.workflowInstanceId = workflowInstanceId;
    this.scopeId = scopeId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VariablesRequestDto that = (VariablesRequestDto) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return scopeId != null ? scopeId.equals(that.scopeId) : that.scopeId == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
    result = 31 * result + (scopeId != null ? scopeId.hashCode() : 0);
    return result;
  }
}
