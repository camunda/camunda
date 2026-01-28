/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Arrays;
import java.util.Objects;

public class VariableEntity
    implements ExporterEntity<VariableEntity>, PartitionedEntity<VariableEntity>, TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private int partitionId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String value;
  @BeforeVersion880 private String fullValue;
  @BeforeVersion880 private boolean isPreview;
  @BeforeVersion880 private Long scopeKey;
  @BeforeVersion880 private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private String bpmnProcessId;

  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @BeforeVersion880 private Long position;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @JsonIgnore private Object[] sortValues;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public VariableEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public VariableEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public VariableEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
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

  public Long getScopeKey() {
    return scopeKey;
  }

  public VariableEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public VariableEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public VariableEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public VariableEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public VariableEntity setTenantId(final String tenantId) {
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

  public Long getPosition() {
    return position;
  }

  public VariableEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public VariableEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            key,
            partitionId,
            name,
            value,
            fullValue,
            isPreview,
            scopeKey,
            processInstanceKey,
            processDefinitionKey,
            bpmnProcessId,
            tenantId,
            position,
            rootProcessInstanceKey);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableEntity that = (VariableEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && partitionId == that.partitionId
        && isPreview == that.isPreview
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue)
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey);
  }

  @Override
  public String toString() {
    return "VariableEntity{"
        + "name='"
        + name
        + '\''
        + ", isPreview="
        + isPreview
        + ", scopeKey="
        + scopeKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", position="
        + position
        + ", rootProcessInstanceKey="
        + rootProcessInstanceKey
        + "} "
        + super.toString();
  }
}
