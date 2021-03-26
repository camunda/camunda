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

  private String processInstanceId;

  private String activityId;

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public SequenceFlowDto setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
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
      .setProcessInstanceId(ConversionUtils.toStringOrNull(sequenceFlowEntity.getProcessInstanceKey()))
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

    if (processInstanceId != null ? !processInstanceId.equals(that.processInstanceId) : that.processInstanceId != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;

  }

  @Override
  public int hashCode() {
    int result = processInstanceId != null ? processInstanceId.hashCode() : 0;
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
