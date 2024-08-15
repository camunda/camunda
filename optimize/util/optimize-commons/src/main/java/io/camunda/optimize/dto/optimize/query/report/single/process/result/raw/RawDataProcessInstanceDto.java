/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.FlowNodeTotalDurationDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class RawDataProcessInstanceDto implements RawDataInstanceDto {

  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected RawDataCountDto counts;
  protected Map<String, FlowNodeTotalDurationDataDto> flowNodeDurations;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long
      duration; // duration in ms. Displayed in Frontend as "Duration" with appropriate unit
  protected String engineName;
  protected String tenantId;

  // Note that for more convenient display in raw data reports, each list of variable values is
  // joined to form one
  // comma separated string
  protected Map<String, Object> variables;

  // Note that the flow node data field can only be included on the Json export response
  protected List<RawDataFlowNodeDataDto> flowNodeInstances;

  public RawDataProcessInstanceDto(
      final String processDefinitionKey,
      final String processDefinitionId,
      final String processInstanceId,
      final RawDataCountDto counts,
      final Map<String, FlowNodeTotalDurationDataDto> flowNodeDurations,
      final String businessKey,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Long duration,
      final String engineName,
      final String tenantId,
      final Map<String, Object> variables,
      final List<RawDataFlowNodeDataDto> flowNodeInstances) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.counts = counts;
    this.flowNodeDurations = flowNodeDurations;
    this.businessKey = businessKey;
    this.startDate = startDate;
    this.endDate = endDate;
    this.duration = duration;
    this.engineName = engineName;
    this.tenantId = tenantId;
    this.variables = variables;
    this.flowNodeInstances = flowNodeInstances;
  }

  public RawDataProcessInstanceDto() {}

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
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

  public RawDataCountDto getCounts() {
    return counts;
  }

  public void setCounts(final RawDataCountDto counts) {
    this.counts = counts;
  }

  public Map<String, FlowNodeTotalDurationDataDto> getFlowNodeDurations() {
    return flowNodeDurations;
  }

  public void setFlowNodeDurations(
      final Map<String, FlowNodeTotalDurationDataDto> flowNodeDurations) {
    this.flowNodeDurations = flowNodeDurations;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(final Long duration) {
    this.duration = duration;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(final String engineName) {
    this.engineName = engineName;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  public List<RawDataFlowNodeDataDto> getFlowNodeInstances() {
    return flowNodeInstances;
  }

  public void setFlowNodeInstances(final List<RawDataFlowNodeDataDto> flowNodeInstances) {
    this.flowNodeInstances = flowNodeInstances;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof RawDataProcessInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $counts = getCounts();
    result = result * PRIME + ($counts == null ? 43 : $counts.hashCode());
    final Object $flowNodeDurations = getFlowNodeDurations();
    result = result * PRIME + ($flowNodeDurations == null ? 43 : $flowNodeDurations.hashCode());
    final Object $businessKey = getBusinessKey();
    result = result * PRIME + ($businessKey == null ? 43 : $businessKey.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    final Object $duration = getDuration();
    result = result * PRIME + ($duration == null ? 43 : $duration.hashCode());
    final Object $engineName = getEngineName();
    result = result * PRIME + ($engineName == null ? 43 : $engineName.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $variables = getVariables();
    result = result * PRIME + ($variables == null ? 43 : $variables.hashCode());
    final Object $flowNodeInstances = getFlowNodeInstances();
    result = result * PRIME + ($flowNodeInstances == null ? 43 : $flowNodeInstances.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RawDataProcessInstanceDto)) {
      return false;
    }
    final RawDataProcessInstanceDto other = (RawDataProcessInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
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
    final Object this$counts = getCounts();
    final Object other$counts = other.getCounts();
    if (this$counts == null ? other$counts != null : !this$counts.equals(other$counts)) {
      return false;
    }
    final Object this$flowNodeDurations = getFlowNodeDurations();
    final Object other$flowNodeDurations = other.getFlowNodeDurations();
    if (this$flowNodeDurations == null
        ? other$flowNodeDurations != null
        : !this$flowNodeDurations.equals(other$flowNodeDurations)) {
      return false;
    }
    final Object this$businessKey = getBusinessKey();
    final Object other$businessKey = other.getBusinessKey();
    if (this$businessKey == null
        ? other$businessKey != null
        : !this$businessKey.equals(other$businessKey)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    final Object this$duration = getDuration();
    final Object other$duration = other.getDuration();
    if (this$duration == null ? other$duration != null : !this$duration.equals(other$duration)) {
      return false;
    }
    final Object this$engineName = getEngineName();
    final Object other$engineName = other.getEngineName();
    if (this$engineName == null
        ? other$engineName != null
        : !this$engineName.equals(other$engineName)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$variables = getVariables();
    final Object other$variables = other.getVariables();
    if (this$variables == null
        ? other$variables != null
        : !this$variables.equals(other$variables)) {
      return false;
    }
    final Object this$flowNodeInstances = getFlowNodeInstances();
    final Object other$flowNodeInstances = other.getFlowNodeInstances();
    if (this$flowNodeInstances == null
        ? other$flowNodeInstances != null
        : !this$flowNodeInstances.equals(other$flowNodeInstances)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "RawDataProcessInstanceDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", counts="
        + getCounts()
        + ", flowNodeDurations="
        + getFlowNodeDurations()
        + ", businessKey="
        + getBusinessKey()
        + ", startDate="
        + getStartDate()
        + ", endDate="
        + getEndDate()
        + ", duration="
        + getDuration()
        + ", engineName="
        + getEngineName()
        + ", tenantId="
        + getTenantId()
        + ", variables="
        + getVariables()
        + ", flowNodeInstances="
        + getFlowNodeInstances()
        + ")";
  }

  public enum Fields {
    processDefinitionKey,
    processDefinitionId,
    processInstanceId,
    businessKey,
    startDate,
    endDate,
    duration,
    engineName,
    tenantId
  }
}
