/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.listview;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;

public class FlowNodeInstanceForListViewEntity extends OperateZeebeEntity<FlowNodeInstanceForListViewEntity> {

  private Long processInstanceKey;
  private String activityId;
  private FlowNodeState activityState;
  private FlowNodeType activityType;
  private Long incidentKey;
  private String errorMessage;
  private Long incidentJobKey;
  private boolean incident;

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

  public Long getIncidentKey() {
    return incidentKey;
  }

  public void setIncidentKey(Long incidentKey) {
    this.incidentKey = incidentKey;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Long getIncidentJobKey() {
    return incidentJobKey;
  }

  public void setIncidentJobKey(Long incidentJobKey) {
    this.incidentJobKey = incidentJobKey;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    FlowNodeInstanceForListViewEntity that = (FlowNodeInstanceForListViewEntity) o;

    if (processInstanceKey != null ? !processInstanceKey.equals(that.processInstanceKey) : that.processInstanceKey != null)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (activityState != that.activityState)
      return false;
    if (activityType != that.activityType)
      return false;
    if (incidentKey != null ? !incidentKey.equals(that.incidentKey) : that.incidentKey != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (incidentJobKey != null ? !incidentJobKey.equals(that.incidentJobKey) : that.incidentJobKey != null)
      return false;
    return joinRelation != null ? joinRelation.equals(that.joinRelation) : that.joinRelation == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (processInstanceKey != null ? processInstanceKey.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (activityState != null ? activityState.hashCode() : 0);
    result = 31 * result + (activityType != null ? activityType.hashCode() : 0);
    result = 31 * result + (incidentKey != null ? incidentKey.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (incidentJobKey != null ? incidentJobKey.hashCode() : 0);
    result = 31 * result + (joinRelation != null ? joinRelation.hashCode() : 0);
    return result;
  }
}
