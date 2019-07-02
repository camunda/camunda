/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

public class SequenceFlowEntity extends  OperateZeebeEntity {

  private Long workflowInstanceId;
  private String activityId;

  public Long getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(Long workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    SequenceFlowEntity that = (SequenceFlowEntity) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
