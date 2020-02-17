/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities.listview;

import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;

import io.zeebe.protocol.record.value.VariableRecordValue;


public class VariableForListViewEntity extends OperateZeebeEntity<VariableForListViewEntity> {

  private Long workflowInstanceKey;
  private Long scopeKey;
  private String varName;
  private String varValue;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }
  
  public static String getIdBy(VariableRecordValue recordValue) {
    return String.format("%s-%s", recordValue.getScopeKey(), recordValue.getName());
  }

  public void setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
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

    if (workflowInstanceKey != null ? !workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey != null)
      return false;
    if (scopeKey != null ? !scopeKey.equals(that.scopeKey) : that.scopeKey != null)
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
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    result = 31 * result + (scopeKey != null ? scopeKey.hashCode() : 0);
    result = 31 * result + (varName != null ? varName.hashCode() : 0);
    result = 31 * result + (varValue != null ? varValue.hashCode() : 0);
    result = 31 * result + (joinRelation != null ? joinRelation.hashCode() : 0);
    return result;
  }
}
