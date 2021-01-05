/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@ApiModel("Workflow instance query")
public class ListViewQueryDto {

  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean canceled;

  @ApiModelProperty(value = "Array of workflow instance ids", allowEmptyValue = true)
  private List<String> ids;

  private String errorMessage;

  private String activityId;

  @ApiModelProperty(value = "Start date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime startDateAfter;

  @ApiModelProperty(value = "Start date before (exclusive)", allowEmptyValue = true)
  private OffsetDateTime startDateBefore;

  @ApiModelProperty(value = "End date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime endDateAfter;

  @ApiModelProperty(value = "End date before (exclusive)", allowEmptyValue = true)
  private OffsetDateTime endDateBefore;

  private List<String> workflowIds;

  private String bpmnProcessId;

  @ApiModelProperty(value = "Workflow version, goes together with bpmnProcessId. Can be null, then all version of the workflow are selected.", allowEmptyValue = true)
  private Integer workflowVersion;

  private List<String> excludeIds;

  private VariablesQueryDto variable;

  private String batchOperationId;

  public ListViewQueryDto() {
  }

  public boolean isRunning() {
    return running;
  }

  public ListViewQueryDto setRunning(boolean running) {
    this.running = running;
    return this;
  }

  public boolean isCompleted() {
    return completed;
  }

  public ListViewQueryDto setCompleted(boolean completed) {
    this.completed = completed;
    return this;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public ListViewQueryDto setIncidents(boolean incidents) {
    this.incidents = incidents;
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public ListViewQueryDto setActive(boolean active) {
    this.active = active;
    return this;
  }

  public boolean isFinished() {
    return finished;
  }

  public ListViewQueryDto setFinished(boolean finished) {
    this.finished = finished;
    return this;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public ListViewQueryDto setCanceled(boolean canceled) {
    this.canceled = canceled;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public ListViewQueryDto setIds(List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public ListViewQueryDto setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public ListViewQueryDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public OffsetDateTime getStartDateAfter() {
    return startDateAfter;
  }

  public ListViewQueryDto setStartDateAfter(OffsetDateTime startDateAfter) {
    this.startDateAfter = startDateAfter;
    return this;
  }

  public OffsetDateTime getStartDateBefore() {
    return startDateBefore;
  }

  public ListViewQueryDto setStartDateBefore(OffsetDateTime startDateBefore) {
    this.startDateBefore = startDateBefore;
    return this;
  }

  public OffsetDateTime getEndDateAfter() {
    return endDateAfter;
  }

  public ListViewQueryDto setEndDateAfter(OffsetDateTime endDateAfter) {
    this.endDateAfter = endDateAfter;
    return this;
  }

  public OffsetDateTime getEndDateBefore() {
    return endDateBefore;
  }

  public ListViewQueryDto setEndDateBefore(OffsetDateTime endDateBefore) {
    this.endDateBefore = endDateBefore;
    return this;
  }

  public List<String> getWorkflowIds() {
    return workflowIds;
  }

  public ListViewQueryDto setWorkflowIds(List<String> workflowIds) {
    this.workflowIds = workflowIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewQueryDto setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Integer getWorkflowVersion() {
    return workflowVersion;
  }

  public ListViewQueryDto setWorkflowVersion(Integer workflowVersion) {
    this.workflowVersion = workflowVersion;
    return this;
  }

  public List<String> getExcludeIds() {
    return excludeIds;
  }

  public ListViewQueryDto setExcludeIds(List<String> excludeIds) {
    this.excludeIds = excludeIds;
    return this;
  }

  public VariablesQueryDto getVariable() {
    return variable;
  }

  public ListViewQueryDto setVariable(VariablesQueryDto variable) {
    this.variable = variable;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public void setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
  }


  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListViewQueryDto that = (ListViewQueryDto) o;
    return running == that.running &&
        active == that.active &&
        incidents == that.incidents &&
        finished == that.finished &&
        completed == that.completed &&
        canceled == that.canceled &&
        Objects.equals(ids, that.ids) &&
        Objects.equals(errorMessage, that.errorMessage) &&
        Objects.equals(activityId, that.activityId) &&
        Objects.equals(startDateAfter, that.startDateAfter) &&
        Objects.equals(startDateBefore, that.startDateBefore) &&
        Objects.equals(endDateAfter, that.endDateAfter) &&
        Objects.equals(endDateBefore, that.endDateBefore) &&
        Objects.equals(workflowIds, that.workflowIds) &&
        Objects.equals(bpmnProcessId, that.bpmnProcessId) &&
        Objects.equals(workflowVersion, that.workflowVersion) &&
        Objects.equals(excludeIds, that.excludeIds) &&
        Objects.equals(variable, that.variable) &&
        Objects.equals(batchOperationId, that.batchOperationId);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(running, active, incidents, finished, completed, canceled, ids, errorMessage,
            activityId, startDateAfter, startDateBefore, endDateAfter, endDateBefore, workflowIds,
            bpmnProcessId, workflowVersion, excludeIds, variable, batchOperationId);
  }
}
