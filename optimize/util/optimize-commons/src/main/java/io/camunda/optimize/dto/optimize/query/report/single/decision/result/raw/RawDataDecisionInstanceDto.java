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
    final int PRIME = 59;
    int result = 1;
    final Object $decisionDefinitionKey = getDecisionDefinitionKey();
    result =
        result * PRIME + ($decisionDefinitionKey == null ? 43 : $decisionDefinitionKey.hashCode());
    final Object $decisionDefinitionId = getDecisionDefinitionId();
    result =
        result * PRIME + ($decisionDefinitionId == null ? 43 : $decisionDefinitionId.hashCode());
    final Object $decisionInstanceId = getDecisionInstanceId();
    result = result * PRIME + ($decisionInstanceId == null ? 43 : $decisionInstanceId.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $evaluationDateTime = getEvaluationDateTime();
    result = result * PRIME + ($evaluationDateTime == null ? 43 : $evaluationDateTime.hashCode());
    final Object $engineName = getEngineName();
    result = result * PRIME + ($engineName == null ? 43 : $engineName.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $inputVariables = getInputVariables();
    result = result * PRIME + ($inputVariables == null ? 43 : $inputVariables.hashCode());
    final Object $outputVariables = getOutputVariables();
    result = result * PRIME + ($outputVariables == null ? 43 : $outputVariables.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RawDataDecisionInstanceDto)) {
      return false;
    }
    final RawDataDecisionInstanceDto other = (RawDataDecisionInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$decisionDefinitionKey = getDecisionDefinitionKey();
    final Object other$decisionDefinitionKey = other.getDecisionDefinitionKey();
    if (this$decisionDefinitionKey == null
        ? other$decisionDefinitionKey != null
        : !this$decisionDefinitionKey.equals(other$decisionDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionId = getDecisionDefinitionId();
    final Object other$decisionDefinitionId = other.getDecisionDefinitionId();
    if (this$decisionDefinitionId == null
        ? other$decisionDefinitionId != null
        : !this$decisionDefinitionId.equals(other$decisionDefinitionId)) {
      return false;
    }
    final Object this$decisionInstanceId = getDecisionInstanceId();
    final Object other$decisionInstanceId = other.getDecisionInstanceId();
    if (this$decisionInstanceId == null
        ? other$decisionInstanceId != null
        : !this$decisionInstanceId.equals(other$decisionInstanceId)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$evaluationDateTime = getEvaluationDateTime();
    final Object other$evaluationDateTime = other.getEvaluationDateTime();
    if (this$evaluationDateTime == null
        ? other$evaluationDateTime != null
        : !this$evaluationDateTime.equals(other$evaluationDateTime)) {
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
    final Object this$inputVariables = getInputVariables();
    final Object other$inputVariables = other.getInputVariables();
    if (this$inputVariables == null
        ? other$inputVariables != null
        : !this$inputVariables.equals(other$inputVariables)) {
      return false;
    }
    final Object this$outputVariables = getOutputVariables();
    final Object other$outputVariables = other.getOutputVariables();
    if (this$outputVariables == null
        ? other$outputVariables != null
        : !this$outputVariables.equals(other$outputVariables)) {
      return false;
    }
    return true;
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
