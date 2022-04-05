/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

public class SequenceFlowEntity extends OperateEntity<SequenceFlowEntity> {

  private Long processInstanceKey;
  private String activityId;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SequenceFlowEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public SequenceFlowEntity setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
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

    if (processInstanceKey != null ? !processInstanceKey.equals(that.processInstanceKey) : that.processInstanceKey != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (processInstanceKey != null ? processInstanceKey.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
