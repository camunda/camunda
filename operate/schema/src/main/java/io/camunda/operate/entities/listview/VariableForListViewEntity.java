/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  private Long position;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);

  public static String getIdBy(long scopeKey, String name) {
    return String.format("%d-%s", scopeKey, name);
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
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

  public Long getPosition() {
    return position;
  }

  public VariableForListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final VariableForListViewEntity that = (VariableForListViewEntity) o;
    return Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(varName, that.varName)
        && Objects.equals(varValue, that.varValue)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(joinRelation, that.joinRelation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processInstanceKey,
        scopeKey,
        varName,
        varValue,
        tenantId,
        position,
        joinRelation);
  }
}
