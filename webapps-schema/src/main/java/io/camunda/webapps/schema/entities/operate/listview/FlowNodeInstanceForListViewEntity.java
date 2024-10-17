/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate.listview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.webapps.schema.entities.operate.OperateZeebeEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlowNodeInstanceForListViewEntity
    extends OperateZeebeEntity<FlowNodeInstanceForListViewEntity> {

  private Long processInstanceKey;
  private String activityId;
  private FlowNodeState activityState;
  private FlowNodeType activityType;
  @Deprecated @JsonIgnore private List<Long> incidentKeys = new ArrayList<>();
  private String errorMessage;
  private boolean incident;
  private boolean jobFailedWithRetriesLeft = false;

  private String tenantId;

  @Deprecated @JsonIgnore private boolean pendingIncident;

  private Long position;
  private Long positionIncident;
  private Long positionJob;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION);

  @JsonIgnore private Long startTime;
  @JsonIgnore private Long endTime;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstanceForListViewEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public FlowNodeInstanceForListViewEntity setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  public FlowNodeState getActivityState() {
    return activityState;
  }

  public FlowNodeInstanceForListViewEntity setActivityState(final FlowNodeState activityState) {
    this.activityState = activityState;
    return this;
  }

  public FlowNodeType getActivityType() {
    return activityType;
  }

  public FlowNodeInstanceForListViewEntity setActivityType(final FlowNodeType activityType) {
    this.activityType = activityType;
    return this;
  }

  public List<Long> getIncidentKeys() {
    return incidentKeys;
  }

  public FlowNodeInstanceForListViewEntity setIncidentKeys(final List<Long> incidentKeys) {
    this.incidentKeys = incidentKeys;
    return this;
  }

  public FlowNodeInstanceForListViewEntity addIncidentKey(final Long incidentKey) {
    incidentKeys.add(incidentKey);
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public FlowNodeInstanceForListViewEntity setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceForListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public boolean isPendingIncident() {
    return pendingIncident;
  }

  public FlowNodeInstanceForListViewEntity setPendingIncident(final boolean pendingIncident) {
    this.pendingIncident = pendingIncident;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(final ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
  }

  public Long getStartTime() {
    return startTime;
  }

  public void setStartTime(final Long startTime) {
    this.startTime = startTime;
  }

  public Long getEndTime() {
    return endTime;
  }

  public void setEndTime(final Long endTime) {
    this.endTime = endTime;
  }

  public boolean isJobFailedWithRetriesLeft() {
    return jobFailedWithRetriesLeft;
  }

  public FlowNodeInstanceForListViewEntity setJobFailedWithRetriesLeft(
      final boolean jobFailedWithRetriesLeft) {
    this.jobFailedWithRetriesLeft = jobFailedWithRetriesLeft;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public FlowNodeInstanceForListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getPositionIncident() {
    return positionIncident;
  }

  public FlowNodeInstanceForListViewEntity setPositionIncident(final Long positionIncident) {
    this.positionIncident = positionIncident;
    return this;
  }

  public Long getPositionJob() {
    return positionJob;
  }

  public FlowNodeInstanceForListViewEntity setPositionJob(final Long positionJob) {
    this.positionJob = positionJob;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processInstanceKey,
        activityId,
        activityState,
        activityType,
        incidentKeys,
        errorMessage,
        incident,
        jobFailedWithRetriesLeft,
        tenantId,
        pendingIncident,
        position,
        positionIncident,
        positionJob,
        joinRelation,
        startTime,
        endTime);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FlowNodeInstanceForListViewEntity that = (FlowNodeInstanceForListViewEntity) o;
    return incident == that.incident
        && jobFailedWithRetriesLeft == that.jobFailedWithRetriesLeft
        && pendingIncident == that.pendingIncident
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(activityId, that.activityId)
        && activityState == that.activityState
        && activityType == that.activityType
        && Objects.equals(incidentKeys, that.incidentKeys)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(positionIncident, that.positionIncident)
        && Objects.equals(positionJob, that.positionJob)
        && Objects.equals(joinRelation, that.joinRelation)
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime);
  }
}
