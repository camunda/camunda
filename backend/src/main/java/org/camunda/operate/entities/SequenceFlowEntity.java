/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

public class SequenceFlowEntity extends  OperateZeebeEntity {

  private Long workflowInstanceKey;
  private String activityId;

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(Long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
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

    if (workflowInstanceKey != null ? !workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
