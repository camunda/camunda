/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;

public class FlowNodeEventDto implements Serializable, OptimizeDto {

  private String id; // == FlowNodeInstanceDto.flowNodeInstanceId
  private String activityId; // == FlowNodeInstanceDto.flowNodeID
  private String activityType;
  private String activityName;
  private OffsetDateTime timestamp;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String tenantId;
  private String engineAlias;
  private String processInstanceId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long durationInMs;
  private Long orderCounter;
  private Boolean canceled;
  private String taskId; // == FlowNodeInstanceDto.userTaskId (null if flowNode is not a userTask)

  public FlowNodeEventDto(
      final String id,
      final String activityId,
      final String activityType,
      final String activityName,
      final OffsetDateTime timestamp,
      final String processDefinitionId,
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String tenantId,
      final String engineAlias,
      final String processInstanceId,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Long durationInMs,
      final Long orderCounter,
      final Boolean canceled,
      final String taskId) {
    this.id = id;
    this.activityId = activityId;
    this.activityType = activityType;
    this.activityName = activityName;
    this.timestamp = timestamp;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.tenantId = tenantId;
    this.engineAlias = engineAlias;
    this.processInstanceId = processInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.durationInMs = durationInMs;
    this.orderCounter = orderCounter;
    this.canceled = canceled;
    this.taskId = taskId;
  }

  public FlowNodeEventDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public String getActivityType() {
    return activityType;
  }

  public void setActivityType(final String activityType) {
    this.activityType = activityType;
  }

  public String getActivityName() {
    return activityName;
  }

  public void setActivityName(final String activityName) {
    this.activityName = activityName;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Long getDurationInMs() {
    return durationInMs;
  }

  public void setDurationInMs(final Long durationInMs) {
    this.durationInMs = durationInMs;
  }

  public Long getOrderCounter() {
    return orderCounter;
  }

  public void setOrderCounter(final Long orderCounter) {
    this.orderCounter = orderCounter;
  }

  public Boolean getCanceled() {
    return canceled;
  }

  public void setCanceled(final Boolean canceled) {
    this.canceled = canceled;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(final String taskId) {
    this.taskId = taskId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeEventDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $activityId = getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $activityType = getActivityType();
    result = result * PRIME + ($activityType == null ? 43 : $activityType.hashCode());
    final Object $activityName = getActivityName();
    result = result * PRIME + ($activityName == null ? 43 : $activityName.hashCode());
    final Object $timestamp = getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersion = getProcessDefinitionVersion();
    result =
        result * PRIME
            + ($processDefinitionVersion == null ? 43 : $processDefinitionVersion.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $engineAlias = getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    final Object $durationInMs = getDurationInMs();
    result = result * PRIME + ($durationInMs == null ? 43 : $durationInMs.hashCode());
    final Object $orderCounter = getOrderCounter();
    result = result * PRIME + ($orderCounter == null ? 43 : $orderCounter.hashCode());
    final Object $canceled = getCanceled();
    result = result * PRIME + ($canceled == null ? 43 : $canceled.hashCode());
    final Object $taskId = getTaskId();
    result = result * PRIME + ($taskId == null ? 43 : $taskId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeEventDto)) {
      return false;
    }
    final FlowNodeEventDto other = (FlowNodeEventDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$activityId = getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$activityType = getActivityType();
    final Object other$activityType = other.getActivityType();
    if (this$activityType == null
        ? other$activityType != null
        : !this$activityType.equals(other$activityType)) {
      return false;
    }
    final Object this$activityName = getActivityName();
    final Object other$activityName = other.getActivityName();
    if (this$activityName == null
        ? other$activityName != null
        : !this$activityName.equals(other$activityName)) {
      return false;
    }
    final Object this$timestamp = getTimestamp();
    final Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null
        ? other$timestamp != null
        : !this$timestamp.equals(other$timestamp)) {
      return false;
    }
    final Object this$processDefinitionId = getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionVersion = getProcessDefinitionVersion();
    final Object other$processDefinitionVersion = other.getProcessDefinitionVersion();
    if (this$processDefinitionVersion == null
        ? other$processDefinitionVersion != null
        : !this$processDefinitionVersion.equals(other$processDefinitionVersion)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$engineAlias = getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    final Object this$durationInMs = getDurationInMs();
    final Object other$durationInMs = other.getDurationInMs();
    if (this$durationInMs == null
        ? other$durationInMs != null
        : !this$durationInMs.equals(other$durationInMs)) {
      return false;
    }
    final Object this$orderCounter = getOrderCounter();
    final Object other$orderCounter = other.getOrderCounter();
    if (this$orderCounter == null
        ? other$orderCounter != null
        : !this$orderCounter.equals(other$orderCounter)) {
      return false;
    }
    final Object this$canceled = getCanceled();
    final Object other$canceled = other.getCanceled();
    if (this$canceled == null ? other$canceled != null : !this$canceled.equals(other$canceled)) {
      return false;
    }
    final Object this$taskId = getTaskId();
    final Object other$taskId = other.getTaskId();
    if (this$taskId == null ? other$taskId != null : !this$taskId.equals(other$taskId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "FlowNodeEventDto(id="
        + getId()
        + ", activityId="
        + getActivityId()
        + ", activityType="
        + getActivityType()
        + ", activityName="
        + getActivityName()
        + ", timestamp="
        + getTimestamp()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersion="
        + getProcessDefinitionVersion()
        + ", tenantId="
        + getTenantId()
        + ", engineAlias="
        + getEngineAlias()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", startDate="
        + getStartDate()
        + ", endDate="
        + getEndDate()
        + ", durationInMs="
        + getDurationInMs()
        + ", orderCounter="
        + getOrderCounter()
        + ", canceled="
        + getCanceled()
        + ", taskId="
        + getTaskId()
        + ")";
  }
}
