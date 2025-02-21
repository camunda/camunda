/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class FlowNodeInstanceEntity
    implements ExporterEntity<FlowNodeInstanceEntity>,
        PartitionedEntity<FlowNodeInstanceEntity>,
        TenantOwned {

  private String id;
  private long key;
  private int partitionId;
  private String flowNodeId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private FlowNodeState state;
  private FlowNodeType type;
  @Deprecated private Long incidentKey;
  private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private String treePath;
  private int level;
  private Long position;
  private boolean incident;
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  private Long scopeKey;

  @JsonIgnore private Object[] sortValues;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public FlowNodeInstanceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public FlowNodeInstanceEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public FlowNodeInstanceEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceEntity setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceEntity setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeState getState() {
    return state;
  }

  public FlowNodeInstanceEntity setState(final FlowNodeState state) {
    this.state = state;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceEntity setType(final FlowNodeType type) {
    this.type = type;
    return this;
  }

  @Deprecated
  public Long getIncidentKey() {
    return incidentKey;
  }

  @Deprecated
  public FlowNodeInstanceEntity setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstanceEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FlowNodeInstanceEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public FlowNodeInstanceEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceEntity setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public int getLevel() {
    return level;
  }

  public FlowNodeInstanceEntity setLevel(final int level) {
    this.level = level;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public FlowNodeInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public FlowNodeInstanceEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public FlowNodeInstanceEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            key,
            partitionId,
            flowNodeId,
            startDate,
            endDate,
            state,
            type,
            incidentKey,
            processInstanceKey,
            processDefinitionKey,
            bpmnProcessId,
            treePath,
            level,
            position,
            incident,
            tenantId,
            scopeKey);
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
    if (!super.equals(o)) {
      return false;
    }
    final FlowNodeInstanceEntity that = (FlowNodeInstanceEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && partitionId == that.partitionId
        && level == that.level
        && incident == that.incident
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && type == that.type
        && Objects.equals(incidentKey, that.incidentKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(treePath, that.treePath)
        && Objects.equals(position, that.position)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(scopeKey, that.scopeKey);
  }
}
