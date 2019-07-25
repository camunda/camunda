/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.listview;

import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

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
  
  public static ListViewQueryDto all() {
    ListViewQueryDto query = new ListViewQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    query.setFinished(true);
    query.setCanceled(true);
    query.setCompleted(true);
    return query;
  }
  
  public static ListViewQueryDto allRunning() {
    ListViewQueryDto query = new ListViewQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    return query;
  }

  public ListViewQueryDto() {
  }

  public boolean isRunning() {
    return running;
  }

  public void setRunning(boolean running) {
    this.running = running;
  }

  public boolean isCompleted() {
    return completed;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public void setIncidents(boolean incidents) {
    this.incidents = incidents;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }

  public List<String> getIds() {
    return ids;
  }

  public void setIds(List<String> ids) {
    this.ids = ids;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public OffsetDateTime getStartDateAfter() {
    return startDateAfter;
  }

  public void setStartDateAfter(OffsetDateTime startDateAfter) {
    this.startDateAfter = startDateAfter;
  }

  public OffsetDateTime getStartDateBefore() {
    return startDateBefore;
  }

  public void setStartDateBefore(OffsetDateTime startDateBefore) {
    this.startDateBefore = startDateBefore;
  }

  public OffsetDateTime getEndDateAfter() {
    return endDateAfter;
  }

  public void setEndDateAfter(OffsetDateTime endDateAfter) {
    this.endDateAfter = endDateAfter;
  }

  public OffsetDateTime getEndDateBefore() {
    return endDateBefore;
  }

  public void setEndDateBefore(OffsetDateTime endDateBefore) {
    this.endDateBefore = endDateBefore;
  }

  public List<String> getWorkflowIds() {
    return workflowIds;
  }

  public void setWorkflowIds(List<String> workflowIds) {
    this.workflowIds = workflowIds;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public Integer getWorkflowVersion() {
    return workflowVersion;
  }

  public void setWorkflowVersion(Integer workflowVersion) {
    this.workflowVersion = workflowVersion;
  }

  public List<String> getExcludeIds() {
    return excludeIds;
  }

  public void setExcludeIds(List<String> excludeIds) {
    this.excludeIds = excludeIds;
  }

  public VariablesQueryDto getVariable() {
    return variable;
  }

  public void setVariable(VariablesQueryDto variable) {
    this.variable = variable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewQueryDto that = (ListViewQueryDto) o;

    if (running != that.running)
      return false;
    if (active != that.active)
      return false;
    if (incidents != that.incidents)
      return false;
    if (finished != that.finished)
      return false;
    if (completed != that.completed)
      return false;
    if (canceled != that.canceled)
      return false;
    if (ids != null ? !ids.equals(that.ids) : that.ids != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (startDateAfter != null ? !startDateAfter.equals(that.startDateAfter) : that.startDateAfter != null)
      return false;
    if (startDateBefore != null ? !startDateBefore.equals(that.startDateBefore) : that.startDateBefore != null)
      return false;
    if (endDateAfter != null ? !endDateAfter.equals(that.endDateAfter) : that.endDateAfter != null)
      return false;
    if (endDateBefore != null ? !endDateBefore.equals(that.endDateBefore) : that.endDateBefore != null)
      return false;
    if (workflowIds != null ? !workflowIds.equals(that.workflowIds) : that.workflowIds != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (workflowVersion != null ? !workflowVersion.equals(that.workflowVersion) : that.workflowVersion != null)
      return false;
    if (excludeIds != null ? !excludeIds.equals(that.excludeIds) : that.excludeIds != null)
      return false;
    return variable != null ? variable.equals(that.variable) : that.variable == null;
  }

  @Override
  public int hashCode() {
    int result = (running ? 1 : 0);
    result = 31 * result + (active ? 1 : 0);
    result = 31 * result + (incidents ? 1 : 0);
    result = 31 * result + (finished ? 1 : 0);
    result = 31 * result + (completed ? 1 : 0);
    result = 31 * result + (canceled ? 1 : 0);
    result = 31 * result + (ids != null ? ids.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (startDateAfter != null ? startDateAfter.hashCode() : 0);
    result = 31 * result + (startDateBefore != null ? startDateBefore.hashCode() : 0);
    result = 31 * result + (endDateAfter != null ? endDateAfter.hashCode() : 0);
    result = 31 * result + (endDateBefore != null ? endDateBefore.hashCode() : 0);
    result = 31 * result + (workflowIds != null ? workflowIds.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (workflowVersion != null ? workflowVersion.hashCode() : 0);
    result = 31 * result + (excludeIds != null ? excludeIds.hashCode() : 0);
    result = 31 * result + (variable != null ? variable.hashCode() : 0);
    return result;
  }
}
