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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
