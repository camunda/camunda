/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence.incident;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;

/**
 * A flattened incident document that combines process instance context with incident-specific
 * fields, for storing in the FlatIncidentIndex.
 */
public class FlatIncidentDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String id;
  private OffsetDateTime createTime;
  private OffsetDateTime endTime;
  private Long durationInMs;
  private IncidentType incidentType;
  private String activityId;
  private String failedActivityId;
  private String incidentMessage;
  private IncidentStatus incidentStatus;
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;
  private int partition;
  private int ordinal;

  public FlatIncidentDto() {}

  public FlatIncidentDto(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final String id,
      final OffsetDateTime createTime,
      final OffsetDateTime endTime,
      final Long durationInMs,
      final IncidentType incidentType,
      final String activityId,
      final String failedActivityId,
      final String incidentMessage,
      final IncidentStatus incidentStatus,
      final String definitionKey,
      final String definitionVersion,
      final String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.id = id;
    this.createTime = createTime;
    this.endTime = endTime;
    this.durationInMs = durationInMs;
    this.incidentType = incidentType;
    this.activityId = activityId;
    this.failedActivityId = failedActivityId;
    this.incidentMessage = incidentMessage;
    this.incidentStatus = incidentStatus;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
  }

  public static FlatIncidentDto fromProcessInstanceAndIncident(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final IncidentDto incident) {
    return new FlatIncidentDto(
        processDefinitionKey,
        processDefinitionVersion,
        processDefinitionId,
        incident.getProcessInstanceId(),
        incident.getId(),
        incident.getCreateTime(),
        incident.getEndTime(),
        incident.getDurationInMs(),
        incident.getIncidentType(),
        incident.getActivityId(),
        incident.getFailedActivityId(),
        incident.getIncidentMessage(),
        incident.getIncidentStatus(),
        incident.getDefinitionKey(),
        incident.getDefinitionVersion(),
        incident.getTenantId());
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

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public OffsetDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(final OffsetDateTime createTime) {
    this.createTime = createTime;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public Long getDurationInMs() {
    return durationInMs;
  }

  public void setDurationInMs(final Long durationInMs) {
    this.durationInMs = durationInMs;
  }

  public IncidentType getIncidentType() {
    return incidentType;
  }

  public void setIncidentType(final IncidentType incidentType) {
    this.incidentType = incidentType;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public String getFailedActivityId() {
    return failedActivityId;
  }

  public void setFailedActivityId(final String failedActivityId) {
    this.failedActivityId = failedActivityId;
  }

  public String getIncidentMessage() {
    return incidentMessage;
  }

  public void setIncidentMessage(final String incidentMessage) {
    this.incidentMessage = incidentMessage;
  }

  public IncidentStatus getIncidentStatus() {
    return incidentStatus;
  }

  public void setIncidentStatus(final IncidentStatus incidentStatus) {
    this.incidentStatus = incidentStatus;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public void setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public String getDefinitionVersion() {
    return definitionVersion;
  }

  public void setDefinitionVersion(final String definitionVersion) {
    this.definitionVersion = definitionVersion;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int getPartition() {
    return partition;
  }

  public void setPartition(final int partition) {
    this.partition = partition;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }
}
