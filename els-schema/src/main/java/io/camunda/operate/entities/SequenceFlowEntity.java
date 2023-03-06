/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.util.Objects;

public class SequenceFlowEntity extends OperateEntity<SequenceFlowEntity> {

  private Long processInstanceKey;
  private Long processDefinitionKey;
  private String bpmnProcessId;
  private String activityId;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SequenceFlowEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public SequenceFlowEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public SequenceFlowEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
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
    return Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(processDefinitionKey, that.processDefinitionKey) && Objects.equals(
        bpmnProcessId, that.bpmnProcessId) && Objects.equals(activityId, that.activityId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processInstanceKey, processDefinitionKey, bpmnProcessId, activityId);
  }
}
