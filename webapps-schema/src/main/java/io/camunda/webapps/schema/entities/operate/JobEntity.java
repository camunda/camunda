/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class JobEntity
    implements ExporterEntity<JobEntity>, PartitionedEntity<JobEntity>, TenantOwned {

  private String id;
  private long key;
  private int partitionId;
  private Long processInstanceKey;
  private Long flowNodeInstanceId;
  private String flowNodeId;

  /** Attention! This field will be filled in only for data imported after v. 8.7.0. */
  private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.7.0. */
  private String bpmnProcessId;

  private String tenantId;
  private String type;
  private String worker;
  private Integer retries;
  private String state;
  private String errorMessage;
  private String errorCode;
  private OffsetDateTime deadline;
  private OffsetDateTime endTime;
  private Map<String, String> customHeaders;
  private boolean jobFailedWithRetriesLeft;
  private String jobKind;

  private String listenerEventType;

  private Long position;

  private Boolean denied;
  private String deniedReason;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public JobEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public JobEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public JobEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public JobEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public JobEntity setFlowNodeInstanceId(final Long flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public JobEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public JobEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getType() {
    return type;
  }

  public JobEntity setType(final String type) {
    this.type = type;
    return this;
  }

  public String getWorker() {
    return worker;
  }

  public JobEntity setWorker(final String worker) {
    this.worker = worker;
    return this;
  }

  public String getState() {
    return state;
  }

  public JobEntity setState(final String state) {
    this.state = state;
    return this;
  }

  public Integer getRetries() {
    return retries;
  }

  public JobEntity setRetries(final Integer retries) {
    this.retries = retries;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public JobEntity setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public JobEntity setErrorCode(final String errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public OffsetDateTime getDeadline() {
    return deadline;
  }

  public JobEntity setDeadline(final OffsetDateTime deadline) {
    this.deadline = deadline;
    return this;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public JobEntity setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public JobEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public boolean isJobFailedWithRetriesLeft() {
    return jobFailedWithRetriesLeft;
  }

  public JobEntity setJobFailedWithRetriesLeft(final boolean jobFailedWithRetriesLeft) {
    this.jobFailedWithRetriesLeft = jobFailedWithRetriesLeft;
    return this;
  }

  public String getJobKind() {
    return jobKind;
  }

  public JobEntity setJobKind(final String jobKind) {
    this.jobKind = jobKind;
    return this;
  }

  public String getListenerEventType() {
    return listenerEventType;
  }

  public JobEntity setListenerEventType(final String listenerEventType) {
    this.listenerEventType = listenerEventType;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public JobEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public JobEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public JobEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Boolean isDenied() {
    return denied;
  }

  public JobEntity setDenied(final Boolean denied) {
    this.denied = denied;
    return this;
  }

  public String getDeniedReason() {
    return deniedReason;
  }

  public JobEntity setDeniedReason(final String deniedReason) {
    this.deniedReason = deniedReason;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        partitionId,
        processInstanceKey,
        flowNodeInstanceId,
        flowNodeId,
        processDefinitionKey,
        bpmnProcessId,
        tenantId,
        type,
        worker,
        retries,
        state,
        errorMessage,
        errorCode,
        deadline,
        endTime,
        customHeaders,
        jobFailedWithRetriesLeft,
        jobKind,
        listenerEventType,
        position,
        denied,
        deniedReason);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobEntity jobEntity = (JobEntity) o;
    return Objects.equals(id, jobEntity.id)
        && key == jobEntity.key
        && partitionId == jobEntity.partitionId
        && jobFailedWithRetriesLeft == jobEntity.jobFailedWithRetriesLeft
        && Objects.equals(processInstanceKey, jobEntity.processInstanceKey)
        && Objects.equals(flowNodeInstanceId, jobEntity.flowNodeInstanceId)
        && Objects.equals(flowNodeId, jobEntity.flowNodeId)
        && Objects.equals(processDefinitionKey, jobEntity.processDefinitionKey)
        && Objects.equals(bpmnProcessId, jobEntity.bpmnProcessId)
        && Objects.equals(tenantId, jobEntity.tenantId)
        && Objects.equals(type, jobEntity.type)
        && Objects.equals(worker, jobEntity.worker)
        && Objects.equals(retries, jobEntity.retries)
        && Objects.equals(state, jobEntity.state)
        && Objects.equals(errorMessage, jobEntity.errorMessage)
        && Objects.equals(errorCode, jobEntity.errorCode)
        && Objects.equals(deadline, jobEntity.deadline)
        && Objects.equals(endTime, jobEntity.endTime)
        && Objects.equals(customHeaders, jobEntity.customHeaders)
        && Objects.equals(jobKind, jobEntity.jobKind)
        && Objects.equals(listenerEventType, jobEntity.listenerEventType)
        && Objects.equals(position, jobEntity.position)
        && Objects.equals(denied, jobEntity.denied)
        && Objects.equals(deniedReason, jobEntity.deniedReason);
  }

  @Override
  public String toString() {
    return "JobEntity{"
        + "processInstanceKey="
        + processInstanceKey
        + ", flowNodeInstanceId="
        + flowNodeInstanceId
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", retries="
        + retries
        + ", state='"
        + state
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", errorCode='"
        + errorCode
        + '\''
        + ", deadline="
        + deadline
        + ", endTime="
        + endTime
        + ", customHeaders="
        + customHeaders
        + ", jobFailedWithRetriesLeft="
        + jobFailedWithRetriesLeft
        + ", jobKind='"
        + jobKind
        + '\''
        + ", listenerEventType='"
        + listenerEventType
        + '\''
        + ", position="
        + position
        + ", jobDenied="
        + denied
        + ", jobDeniedReason="
        + deniedReason
        + "} "
        + super.toString();
  }
}
