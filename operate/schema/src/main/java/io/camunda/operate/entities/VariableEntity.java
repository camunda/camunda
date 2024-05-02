/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Objects;

public class VariableEntity extends OperateZeebeEntity<VariableEntity> {

  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;
  private Long scopeKey;
  private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private String tenantId = DEFAULT_TENANT_ID;

  @JsonIgnore private Object[] sortValues;

  public String getName() {
    return name;
  }

  public VariableEntity setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableEntity setValue(String value) {
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

  public Long getScopeKey() {
    return scopeKey;
  }

  public VariableEntity setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public VariableEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public VariableEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public VariableEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public VariableEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public boolean equals(Object o) {
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
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            name,
            value,
            fullValue,
            isPreview,
            scopeKey,
            processInstanceKey,
            processDefinitionKey,
            bpmnProcessId,
            tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
