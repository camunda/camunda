/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Objects;

public class VariableEntity extends OperateZeebeEntity<VariableEntity> {

  private String name;
  private String value;
  private Long scopeKey;
  private Long processInstanceKey;

  @JsonIgnore
  private Object[] sortValues;

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

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public VariableEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
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
    final VariableEntity that = (VariableEntity) o;
    return Objects.equals(name, that.name) &&
        Objects.equals(value, that.value) &&
        Objects.equals(scopeKey, that.scopeKey) &&
        Objects.equals(processInstanceKey, that.processInstanceKey) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(super.hashCode(), name, value, scopeKey, processInstanceKey);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
