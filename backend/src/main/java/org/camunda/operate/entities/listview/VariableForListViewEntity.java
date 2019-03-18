/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities.listview;

import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;

public class VariableForListViewEntity extends OperateZeebeEntity {

  private String workflowInstanceId;
  private String scopeId;
  private String varName;
  private String varValue;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);

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

  public String getVarName() {
    return varName;
  }

  public void setVarName(String varName) {
    this.varName = varName;
  }

  public String getVarValue() {
    return varValue;
  }

  public void setVarValue(String varValue) {
    this.varValue = varValue;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    VariableForListViewEntity that = (VariableForListViewEntity) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    if (scopeId != null ? !scopeId.equals(that.scopeId) : that.scopeId != null)
      return false;
    if (varName != null ? !varName.equals(that.varName) : that.varName != null)
      return false;
    if (varValue != null ? !varValue.equals(that.varValue) : that.varValue != null)
      return false;
    return joinRelation != null ? joinRelation.equals(that.joinRelation) : that.joinRelation == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (scopeId != null ? scopeId.hashCode() : 0);
    result = 31 * result + (varName != null ? varName.hashCode() : 0);
    result = 31 * result + (varValue != null ? varValue.hashCode() : 0);
    result = 31 * result + (joinRelation != null ? joinRelation.hashCode() : 0);
    return result;
  }
}
