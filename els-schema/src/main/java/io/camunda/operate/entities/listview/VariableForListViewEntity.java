/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.listview;

import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;

import java.util.Objects;

public class VariableForListViewEntity extends OperateZeebeEntity {

  private Long processInstanceKey;
  private Long scopeKey;
  private String varName;
  private String varValue;

  private String tenantId;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public static String getIdBy(long scopeKey, String name) {
    return String.format("%d-%s", scopeKey, name);
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
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

  public String getTenantId() {
    return tenantId;
  }

  public VariableForListViewEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
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
    return Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(scopeKey,
        that.scopeKey) && Objects.equals(varName, that.varName) && Objects.equals(varValue,
        that.varValue) && Objects.equals(tenantId, that.tenantId) && Objects.equals(joinRelation, that.joinRelation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processInstanceKey, scopeKey, varName, varValue, tenantId, joinRelation);
  }
}
