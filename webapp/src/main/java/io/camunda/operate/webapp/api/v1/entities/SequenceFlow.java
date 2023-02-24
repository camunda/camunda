/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceFlow {

  public static final String ID = SequenceFlowTemplate.ID, ACTIVITY_ID = SequenceFlowTemplate.ACTIVITY_ID, PROCESS_INSTANCE_KEY = SequenceFlowTemplate.PROCESS_INSTANCE_KEY;

  private String id;
  private String activityId;
  private Long processInstanceKey;

  public String getId() {
    return id;
  }

  public SequenceFlow setId(String id) {
    this.id = id;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public SequenceFlow setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SequenceFlow setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public SequenceFlow() {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SequenceFlow that = (SequenceFlow) o;
    return Objects.equals(id, that.id) && Objects.equals(activityId, that.activityId) && Objects.equals(processInstanceKey, that.processInstanceKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, activityId, processInstanceKey);
  }

  @Override
  public String toString() {
    return "SequenceFlow{" + "id='" + id + '\'' + ", activityId='" + activityId + '\'' + ", processInstanceKey=" + processInstanceKey + '}';
  }
}
