/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricUserTaskInstanceDto implements EngineDto {
  private String id;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private String executionId;
  private String caseDefinitionKey;
  private String caseDefinitionId;
  private String caseInstanceId;
  private String caseExecutionId;
  private String activityInstanceId;
  private String name;
  private String description;
  private String deleteReason;
  private String owner;
  private String assignee;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private Long duration;
  private String taskDefinitionKey;
  private int priority;
  private OffsetDateTime due;
  private String parentTaskId;
  private OffsetDateTime followUp;
  private String tenantId;
  private OffsetDateTime removalTime;
  private String rootProcessInstanceId;

  public HistoricUserTaskInstanceDto() {
  }

  @Override
  public String getId() {
    return this.id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
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

  public String getExecutionId() {
    return executionId;
  }

  public void setExecutionId(final String executionId) {
    this.executionId = executionId;
  }

  public String getCaseDefinitionKey() {
    return caseDefinitionKey;
  }

  public void setCaseDefinitionKey(final String caseDefinitionKey) {
    this.caseDefinitionKey = caseDefinitionKey;
  }

  public String getCaseDefinitionId() {
    return caseDefinitionId;
  }

  public void setCaseDefinitionId(final String caseDefinitionId) {
    this.caseDefinitionId = caseDefinitionId;
  }

  public String getCaseInstanceId() {
    return caseInstanceId;
  }

  public void setCaseInstanceId(final String caseInstanceId) {
    this.caseInstanceId = caseInstanceId;
  }

  public String getCaseExecutionId() {
    return caseExecutionId;
  }

  public void setCaseExecutionId(final String caseExecutionId) {
    this.caseExecutionId = caseExecutionId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(final String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public void setDeleteReason(final String deleteReason) {
    this.deleteReason = deleteReason;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(final Long duration) {
    this.duration = duration;
  }

  public String getTaskDefinitionKey() {
    return taskDefinitionKey;
  }

  public void setTaskDefinitionKey(final String taskDefinitionKey) {
    this.taskDefinitionKey = taskDefinitionKey;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(final int priority) {
    this.priority = priority;
  }

  public OffsetDateTime getDue() {
    return due;
  }

  public void setDue(final OffsetDateTime due) {
    this.due = due;
  }

  public String getParentTaskId() {
    return parentTaskId;
  }

  public void setParentTaskId(final String parentTaskId) {
    this.parentTaskId = parentTaskId;
  }

  public OffsetDateTime getFollowUp() {
    return followUp;
  }

  public void setFollowUp(final OffsetDateTime followUp) {
    this.followUp = followUp;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OffsetDateTime getRemovalTime() {
    return removalTime;
  }

  public void setRemovalTime(final OffsetDateTime removalTime) {
    this.removalTime = removalTime;
  }

  public String getRootProcessInstanceId() {
    return rootProcessInstanceId;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }
}
