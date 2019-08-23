/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.activity;

public class ActivityInstanceTreeRequestDto {

  private String workflowInstanceId;

  public ActivityInstanceTreeRequestDto() {
  }

  public ActivityInstanceTreeRequestDto(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ActivityInstanceTreeRequestDto that = (ActivityInstanceTreeRequestDto) o;

    return workflowInstanceId != null ? workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId == null;
  }

  @Override
  public int hashCode() {
    return workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
  }
}
