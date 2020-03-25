/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import java.util.List;

import org.camunda.operate.entities.SequenceFlowEntity;
import static org.camunda.operate.util.CollectionUtil.*;
import org.camunda.operate.util.ConversionUtils;

public class SequenceFlowDto {

  private String workflowInstanceId;

  private String activityId;

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public SequenceFlowDto setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public SequenceFlowDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public static SequenceFlowDto createFrom(SequenceFlowEntity sequenceFlowEntity) {
    if (sequenceFlowEntity == null) {
      return null;
    }
    return new SequenceFlowDto()
      .setWorkflowInstanceId(ConversionUtils.toStringOrNull(sequenceFlowEntity.getWorkflowInstanceKey()))
      .setActivityId(sequenceFlowEntity.getActivityId());
  }

  public static List<SequenceFlowDto> createFrom(List<SequenceFlowEntity> sequenceFlowEntities) {
    return map(emptyListWhenNull(sequenceFlowEntities), s -> createFrom(s));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    SequenceFlowDto that = (SequenceFlowDto) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;

  }

  @Override
  public int hashCode() {
    int result = workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
