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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
