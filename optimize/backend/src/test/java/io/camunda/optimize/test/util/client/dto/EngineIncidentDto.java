/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.client.dto;

import java.util.Date;

public class EngineIncidentDto {

  protected String id;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String executionId;
  protected Date incidentTimestamp;
  protected String incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String causeIncidentId;
  protected String rootCauseIncidentId;
  protected String configuration;
  protected String incidentMessage;
  protected String tenantId;
  protected String jobDefinitionId;

  public EngineIncidentDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getExecutionId() {
    return executionId;
  }

  public void setExecutionId(final String executionId) {
    this.executionId = executionId;
  }

  public Date getIncidentTimestamp() {
    return incidentTimestamp;
  }

  public void setIncidentTimestamp(final Date incidentTimestamp) {
    this.incidentTimestamp = incidentTimestamp;
  }

  public String getIncidentType() {
    return incidentType;
  }

  public void setIncidentType(final String incidentType) {
    this.incidentType = incidentType;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public String getFailedActivityId() {
    return failedActivityId;
  }

  public void setFailedActivityId(final String failedActivityId) {
    this.failedActivityId = failedActivityId;
  }

  public String getCauseIncidentId() {
    return causeIncidentId;
  }

  public void setCauseIncidentId(final String causeIncidentId) {
    this.causeIncidentId = causeIncidentId;
  }

  public String getRootCauseIncidentId() {
    return rootCauseIncidentId;
  }

  public void setRootCauseIncidentId(final String rootCauseIncidentId) {
    this.rootCauseIncidentId = rootCauseIncidentId;
  }

  public String getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final String configuration) {
    this.configuration = configuration;
  }

  public String getIncidentMessage() {
    return incidentMessage;
  }

  public void setIncidentMessage(final String incidentMessage) {
    this.incidentMessage = incidentMessage;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getJobDefinitionId() {
    return jobDefinitionId;
  }

  public void setJobDefinitionId(final String jobDefinitionId) {
    this.jobDefinitionId = jobDefinitionId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineIncidentDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "EngineIncidentDto(id="
        + getId()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", executionId="
        + getExecutionId()
        + ", incidentTimestamp="
        + getIncidentTimestamp()
        + ", incidentType="
        + getIncidentType()
        + ", activityId="
        + getActivityId()
        + ", failedActivityId="
        + getFailedActivityId()
        + ", causeIncidentId="
        + getCauseIncidentId()
        + ", rootCauseIncidentId="
        + getRootCauseIncidentId()
        + ", configuration="
        + getConfiguration()
        + ", incidentMessage="
        + getIncidentMessage()
        + ", tenantId="
        + getTenantId()
        + ", jobDefinitionId="
        + getJobDefinitionId()
        + ")";
  }
}
