/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.util.Objects;

/**
 * Represents runtime value of all variables, that exist in the system. Is used to calculate
 * effective variables for tasks.
 */
public class VariableEntity extends TasklistZeebeEntity<VariableEntity> {

  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;
  private String scopeFlowNodeId;
  private String processInstanceId;

  public static String getIdBy(String scopeFlowNodeId, String name) {
    return String.format("%s-%s", scopeFlowNodeId, name);
  }

  public String getName() {
    return name;
  }

  public VariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public VariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public String getScopeFlowNodeId() {
    return scopeFlowNodeId;
  }

  public VariableEntity setScopeFlowNodeId(final String scopeFlowNodeId) {
    this.scopeFlowNodeId = scopeFlowNodeId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public VariableEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
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
    return isPreview == that.isPreview
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue)
        && Objects.equals(scopeFlowNodeId, that.scopeFlowNodeId)
        && Objects.equals(processInstanceId, that.processInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), name, value, fullValue, isPreview, scopeFlowNodeId, processInstanceId);
  }
}
