/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public class IncidentEntity
    implements ExporterEntity<IncidentEntity>, PartitionedEntity<IncidentEntity>, TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private int partitionId;
  @BeforeVersion880 private ErrorType errorType;
  @BeforeVersion880 private String errorMessage;
  // Is only used by binding to ES results
  @BeforeVersion880 private Integer errorMessageHash;
  @BeforeVersion880 private IncidentState state;
  @BeforeVersion880 private String flowNodeId;
  @BeforeVersion880 private Long flowNodeInstanceKey;
  @BeforeVersion880 private Long jobKey;
  @BeforeVersion880 private Long processInstanceKey;
  @BeforeVersion880 private OffsetDateTime creationTime;
  @BeforeVersion880 private Long processDefinitionKey;
  @BeforeVersion880 private String bpmnProcessId;
  @BeforeVersion880 private String treePath;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  @BeforeVersion880 private Long position;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @Deprecated @JsonIgnore private boolean pending = true;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public IncidentEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public IncidentEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public IncidentEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public IncidentEntity setErrorType(final ErrorType errorType) {
    this.errorType = errorType;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public IncidentEntity setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    setErrorMessageHash(errorMessage.hashCode());
    return this;
  }

  public Integer getErrorMessageHash() {
    return errorMessage.hashCode();
  }

  public IncidentEntity setErrorMessageHash(final Integer errorMessageHash) {
    this.errorMessageHash = errorMessageHash;
    return this;
  }

  public IncidentState getState() {
    return state;
  }

  public IncidentEntity setState(final IncidentState state) {
    this.state = state;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public IncidentEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public IncidentEntity setFlowNodeInstanceKey(final Long flowNodeInstanceId) {
    flowNodeInstanceKey = flowNodeInstanceId;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public IncidentEntity setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public IncidentEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public IncidentEntity setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public IncidentEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public IncidentEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public IncidentEntity setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public boolean isPending() {
    return pending;
  }

  public IncidentEntity setPending(final boolean pending) {
    this.pending = pending;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public IncidentEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public IncidentEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public IncidentEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        partitionId,
        errorType,
        errorMessage,
        errorMessageHash,
        state,
        flowNodeId,
        flowNodeInstanceKey,
        jobKey,
        processInstanceKey,
        creationTime,
        processDefinitionKey,
        bpmnProcessId,
        treePath,
        tenantId,
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
    final IncidentEntity incident = (IncidentEntity) o;
    return Objects.equals(id, incident.id)
        && key == incident.key
        && partitionId == incident.partitionId
        && pending == incident.pending
        && errorType == incident.errorType
        && Objects.equals(errorMessage, incident.errorMessage)
        && Objects.equals(errorMessageHash, incident.errorMessageHash)
        && state == incident.state
        && Objects.equals(flowNodeId, incident.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, incident.flowNodeInstanceKey)
        && Objects.equals(jobKey, incident.jobKey)
        && Objects.equals(processInstanceKey, incident.processInstanceKey)
        && Objects.equals(creationTime, incident.creationTime)
        && Objects.equals(processDefinitionKey, incident.processDefinitionKey)
        && Objects.equals(bpmnProcessId, incident.bpmnProcessId)
        && Objects.equals(treePath, incident.treePath)
        && Objects.equals(tenantId, incident.tenantId)
        && Objects.equals(position, incident.position)
        && Objects.equals(rootProcessInstanceKey, incident.rootProcessInstanceKey);
  }

  @Override
  public String toString() {
    return "IncidentEntity{"
        + "key="
        + key
        + ", errorType="
        + errorType
        + ", errorMessageHash="
        + errorMessageHash
        + ", state="
        + state
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceKey="
        + flowNodeInstanceKey
        + ", jobKey="
        + jobKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", creationTime="
        + creationTime
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId="
        + bpmnProcessId
        + ", treePath='"
        + treePath
        + '\''
        + ", pending="
        + pending
        + ", rootProcessInstanceKey="
        + rootProcessInstanceKey
        + '}';
  }
}
