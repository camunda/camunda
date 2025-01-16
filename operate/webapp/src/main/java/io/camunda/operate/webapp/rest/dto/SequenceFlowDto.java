/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.util.ConversionUtils;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;

public class SequenceFlowDto implements CreatableFromEntity<SequenceFlowDto, SequenceFlowEntity> {

  private String processInstanceId;

  private String activityId;

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public SequenceFlowDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public SequenceFlowDto setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  @Override
  public SequenceFlowDto fillFrom(final SequenceFlowEntity entity) {
    return setProcessInstanceId(ConversionUtils.toStringOrNull(entity.getProcessInstanceKey()))
        .setActivityId(entity.getActivityId());
  }

  @Override
  public int hashCode() {
    int result = processInstanceId != null ? processInstanceId.hashCode() : 0;
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final SequenceFlowDto that = (SequenceFlowDto) o;

    if (processInstanceId != null
        ? !processInstanceId.equals(that.processInstanceId)
        : that.processInstanceId != null) {
      return false;
    }
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;
  }
}
