/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.listview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlowNodeInstanceForListViewEntity extends OperateZeebeEntity<FlowNodeInstanceForListViewEntity> {

  private Long processInstanceKey;
  private String activityId;
  private FlowNodeState activityState;
  private FlowNodeType activityType;
  @Deprecated
  @JsonIgnore
  private List<Long> incidentKeys = new ArrayList<>();
  private String errorMessage;
  private boolean incident;

  @Deprecated
  @JsonIgnore
  private boolean pendingIncident;

  private ListViewJoinRelation joinRelation = new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION);

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public FlowNodeState getActivityState() {
    return activityState;
  }

  public void setActivityState(FlowNodeState activityState) {
    this.activityState = activityState;
  }

  public FlowNodeType getActivityType() {
    return activityType;
  }

  public void setActivityType(FlowNodeType activityType) {
    this.activityType = activityType;
  }

  public List<Long> getIncidentKeys() {
    return incidentKeys;
  }

  public FlowNodeInstanceForListViewEntity setIncidentKeys(List<Long> incidentKeys) {
    this.incidentKeys = incidentKeys;
    return this;
  }

  public FlowNodeInstanceForListViewEntity addIncidentKey(Long incidentKey) {
    this.incidentKeys.add(incidentKey);
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public boolean isPendingIncident() {
    return pendingIncident;
  }

  public FlowNodeInstanceForListViewEntity setPendingIncident(boolean pendingIncident) {
    this.pendingIncident = pendingIncident;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    FlowNodeInstanceForListViewEntity that = (FlowNodeInstanceForListViewEntity) o;
    return incident == that.incident && Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(
        activityId,
        that.activityId) && activityState == that.activityState && activityType == that.activityType && Objects.equals(
        errorMessage, that.errorMessage) && Objects.equals(joinRelation, that.joinRelation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), processInstanceKey, activityId, activityState, activityType, errorMessage,
        incident, joinRelation);
  }
}
