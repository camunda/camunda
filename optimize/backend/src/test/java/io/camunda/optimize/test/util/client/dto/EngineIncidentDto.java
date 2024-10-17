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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $executionId = getExecutionId();
    result = result * PRIME + ($executionId == null ? 43 : $executionId.hashCode());
    final Object $incidentTimestamp = getIncidentTimestamp();
    result = result * PRIME + ($incidentTimestamp == null ? 43 : $incidentTimestamp.hashCode());
    final Object $incidentType = getIncidentType();
    result = result * PRIME + ($incidentType == null ? 43 : $incidentType.hashCode());
    final Object $activityId = getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $failedActivityId = getFailedActivityId();
    result = result * PRIME + ($failedActivityId == null ? 43 : $failedActivityId.hashCode());
    final Object $causeIncidentId = getCauseIncidentId();
    result = result * PRIME + ($causeIncidentId == null ? 43 : $causeIncidentId.hashCode());
    final Object $rootCauseIncidentId = getRootCauseIncidentId();
    result = result * PRIME + ($rootCauseIncidentId == null ? 43 : $rootCauseIncidentId.hashCode());
    final Object $configuration = getConfiguration();
    result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
    final Object $incidentMessage = getIncidentMessage();
    result = result * PRIME + ($incidentMessage == null ? 43 : $incidentMessage.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $jobDefinitionId = getJobDefinitionId();
    result = result * PRIME + ($jobDefinitionId == null ? 43 : $jobDefinitionId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineIncidentDto)) {
      return false;
    }
    final EngineIncidentDto other = (EngineIncidentDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$processDefinitionId = getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$executionId = getExecutionId();
    final Object other$executionId = other.getExecutionId();
    if (this$executionId == null
        ? other$executionId != null
        : !this$executionId.equals(other$executionId)) {
      return false;
    }
    final Object this$incidentTimestamp = getIncidentTimestamp();
    final Object other$incidentTimestamp = other.getIncidentTimestamp();
    if (this$incidentTimestamp == null
        ? other$incidentTimestamp != null
        : !this$incidentTimestamp.equals(other$incidentTimestamp)) {
      return false;
    }
    final Object this$incidentType = getIncidentType();
    final Object other$incidentType = other.getIncidentType();
    if (this$incidentType == null
        ? other$incidentType != null
        : !this$incidentType.equals(other$incidentType)) {
      return false;
    }
    final Object this$activityId = getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$failedActivityId = getFailedActivityId();
    final Object other$failedActivityId = other.getFailedActivityId();
    if (this$failedActivityId == null
        ? other$failedActivityId != null
        : !this$failedActivityId.equals(other$failedActivityId)) {
      return false;
    }
    final Object this$causeIncidentId = getCauseIncidentId();
    final Object other$causeIncidentId = other.getCauseIncidentId();
    if (this$causeIncidentId == null
        ? other$causeIncidentId != null
        : !this$causeIncidentId.equals(other$causeIncidentId)) {
      return false;
    }
    final Object this$rootCauseIncidentId = getRootCauseIncidentId();
    final Object other$rootCauseIncidentId = other.getRootCauseIncidentId();
    if (this$rootCauseIncidentId == null
        ? other$rootCauseIncidentId != null
        : !this$rootCauseIncidentId.equals(other$rootCauseIncidentId)) {
      return false;
    }
    final Object this$configuration = getConfiguration();
    final Object other$configuration = other.getConfiguration();
    if (this$configuration == null
        ? other$configuration != null
        : !this$configuration.equals(other$configuration)) {
      return false;
    }
    final Object this$incidentMessage = getIncidentMessage();
    final Object other$incidentMessage = other.getIncidentMessage();
    if (this$incidentMessage == null
        ? other$incidentMessage != null
        : !this$incidentMessage.equals(other$incidentMessage)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$jobDefinitionId = getJobDefinitionId();
    final Object other$jobDefinitionId = other.getJobDefinitionId();
    if (this$jobDefinitionId == null
        ? other$jobDefinitionId != null
        : !this$jobDefinitionId.equals(other$jobDefinitionId)) {
      return false;
    }
    return true;
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
