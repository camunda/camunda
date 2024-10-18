/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import java.util.Map;

public class RawDataDecisionInstanceDto implements RawDataInstanceDto {

  protected String decisionDefinitionKey;
  protected String decisionDefinitionId;
  protected String decisionInstanceId;
  protected String processInstanceId;
  protected OffsetDateTime evaluationDateTime;
  protected String engineName;
  protected String tenantId;
  protected Map<String, InputVariableEntry> inputVariables;
  protected Map<String, OutputVariableEntry> outputVariables;

  public RawDataDecisionInstanceDto(
      final String decisionDefinitionKey,
      final String decisionDefinitionId,
      final String decisionInstanceId,
      final String processInstanceId,
      final OffsetDateTime evaluationDateTime,
      final String engineName,
      final String tenantId,
      final Map<String, InputVariableEntry> inputVariables,
      final Map<String, OutputVariableEntry> outputVariables) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionInstanceId = decisionInstanceId;
    this.processInstanceId = processInstanceId;
    this.evaluationDateTime = evaluationDateTime;
    this.engineName = engineName;
    this.tenantId = tenantId;
    this.inputVariables = inputVariables;
    this.outputVariables = outputVariables;
  }

  public RawDataDecisionInstanceDto() {}

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public void setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  public void setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public OffsetDateTime getEvaluationDateTime() {
    return evaluationDateTime;
  }

  public void setEvaluationDateTime(final OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
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

  public Map<String, InputVariableEntry> getInputVariables() {
    return inputVariables;
  }

  public void setInputVariables(final Map<String, InputVariableEntry> inputVariables) {
    this.inputVariables = inputVariables;
  }

  public Map<String, OutputVariableEntry> getOutputVariables() {
    return outputVariables;
  }

  public void setOutputVariables(final Map<String, OutputVariableEntry> outputVariables) {
    this.outputVariables = outputVariables;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof RawDataDecisionInstanceDto;
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
    return "RawDataDecisionInstanceDto(decisionDefinitionKey="
        + getDecisionDefinitionKey()
        + ", decisionDefinitionId="
        + getDecisionDefinitionId()
        + ", decisionInstanceId="
        + getDecisionInstanceId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", evaluationDateTime="
        + getEvaluationDateTime()
        + ", engineName="
        + getEngineName()
        + ", tenantId="
        + getTenantId()
        + ", inputVariables="
        + getInputVariables()
        + ", outputVariables="
        + getOutputVariables()
        + ")";
  }

  public enum Fields {
    decisionDefinitionKey,
    decisionDefinitionId,
    decisionInstanceId,
    processInstanceId,
    evaluationDateTime,
    engineName,
    tenantId
  }
}
