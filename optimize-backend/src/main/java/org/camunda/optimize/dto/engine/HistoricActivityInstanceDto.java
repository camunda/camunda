package org.camunda.optimize.dto.engine;

import java.util.Date;

public class HistoricActivityInstanceDto {

  private String id;
  private String parentActivityInstanceId;
  private String activityId;
  private String activityName;
  private String activityType;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private String executionId;
  private String taskId;
  private String calledProcessInstanceId;
  private String calledCaseInstanceId;
  private String assignee;
  private Date startTime;
  private Date endTime;
  private Long durationInMillis;
  private Boolean canceled;
  private Boolean completeScope;
  private String tenantId;

  public String getId() {
    return id;
  }

  public String getParentActivityInstanceId() {
    return parentActivityInstanceId;
  }

  public String getActivityId() {
    return activityId;
  }

  public String getActivityName() {
    return activityName;
  }

  public String getActivityType() {
    return activityType;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getCalledProcessInstanceId() {
    return calledProcessInstanceId;
  }

  public String getCalledCaseInstanceId() {
    return calledCaseInstanceId;
  }

  public String getAssignee() {
    return assignee;
  }

  public Date getStartTime() {
    return startTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  public Long getDurationInMillis() {
    return durationInMillis;
  }

  public Boolean getCanceled() {
    return canceled;
  }

  public Boolean getCompleteScope() {
    return completeScope;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setParentActivityInstanceId(String parentActivityInstanceId) {
    this.parentActivityInstanceId = parentActivityInstanceId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public void setActivityName(String activityName) {
    this.activityName = activityName;
  }

  public void setActivityType(String activityType) {
    this.activityType = activityType;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setExecutionId(String executionId) {
    this.executionId = executionId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public void setCalledProcessInstanceId(String calledProcessInstanceId) {
    this.calledProcessInstanceId = calledProcessInstanceId;
  }

  public void setCalledCaseInstanceId(String calledCaseInstanceId) {
    this.calledCaseInstanceId = calledCaseInstanceId;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  public void setDurationInMillis(Long durationInMillis) {
    this.durationInMillis = durationInMillis;
  }

  public void setCanceled(Boolean canceled) {
    this.canceled = canceled;
  }

  public void setCompleteScope(Boolean completeScope) {
    this.completeScope = completeScope;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
