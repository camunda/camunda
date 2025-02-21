/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate.listview;

import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class ProcessInstanceForListViewEntity
    implements ExporterEntity<ProcessInstanceForListViewEntity>,
        PartitionedEntity<ProcessInstanceForListViewEntity>,
        TenantOwned {

  private String id;
  private long key;
  private int partitionId;
  private Long processDefinitionKey;
  private String processName;
  private Integer processVersion;
  private String processVersionTag;
  private String bpmnProcessId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private ProcessInstanceState state;

  private List<String> batchOperationIds;

  private Long parentProcessInstanceKey;

  private Long parentFlowNodeInstanceKey;

  private String treePath;

  private boolean incident;

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(PROCESS_INSTANCE_JOIN_RELATION);

  private Long position;

  @JsonIgnore private Object[] sortValues;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ProcessInstanceForListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ProcessInstanceForListViewEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public ProcessInstanceForListViewEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return getKey();
  }

  public ProcessInstanceForListViewEntity setProcessInstanceKey(final Long processInstanceKey) {
    setKey(processInstanceKey);
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceForListViewEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public ProcessInstanceForListViewEntity setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstanceForListViewEntity setProcessVersion(final Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getProcessVersionTag() {
    return processVersionTag;
  }

  public ProcessInstanceForListViewEntity setProcessVersionTag(final String processVersionTag) {
    this.processVersionTag = processVersionTag;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public ProcessInstanceForListViewEntity setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public ProcessInstanceForListViewEntity setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public ProcessInstanceState getState() {
    return state;
  }

  public ProcessInstanceForListViewEntity setState(final ProcessInstanceState state) {
    this.state = state;
    return this;
  }

  public List<String> getBatchOperationIds() {
    return batchOperationIds;
  }

  public ProcessInstanceForListViewEntity setBatchOperationIds(
      final List<String> batchOperationIds) {
    this.batchOperationIds = batchOperationIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstanceForListViewEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentProcessInstanceKey(
      final Long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstanceForListViewEntity setParentFlowNodeInstanceKey(
      final Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public ProcessInstanceForListViewEntity setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public ProcessInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstanceForListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public ProcessInstanceForListViewEntity setJoinRelation(final ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public ProcessInstanceForListViewEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public ProcessInstanceForListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        partitionId,
        processDefinitionKey,
        processName,
        processVersion,
        processVersionTag,
        bpmnProcessId,
        startDate,
        endDate,
        state,
        batchOperationIds,
        parentProcessInstanceKey,
        parentFlowNodeInstanceKey,
        treePath,
        incident,
        tenantId,
        joinRelation,
        position);
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
    final ProcessInstanceForListViewEntity that = (ProcessInstanceForListViewEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && partitionId == that.partitionId
        && incident == that.incident
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(processVersionTag, that.processVersionTag)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && state == that.state
        && Objects.equals(batchOperationIds, that.batchOperationIds)
        && Objects.equals(parentProcessInstanceKey, that.parentProcessInstanceKey)
        && Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey)
        && Objects.equals(treePath, that.treePath)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(joinRelation, that.joinRelation)
        && Objects.equals(position, that.position);
  }
}
