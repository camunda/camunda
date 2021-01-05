/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

@Deprecated
public class EventQueryDto {

  private String workflowInstanceId;

  public EventQueryDto() {
  }

  public EventQueryDto(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public EventQueryDto(String workflowInstanceId, String activityInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
    this.activityInstanceId = activityInstanceId;
  }

  private String activityInstanceId;

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    EventQueryDto that = (EventQueryDto) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return activityInstanceId != null ? activityInstanceId.equals(that.activityInstanceId) : that.activityInstanceId == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
    result = 31 * result + (activityInstanceId != null ? activityInstanceId.hashCode() : 0);
    return result;
  }
}
