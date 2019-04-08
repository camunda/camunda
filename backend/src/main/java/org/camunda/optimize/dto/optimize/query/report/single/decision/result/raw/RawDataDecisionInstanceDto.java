/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class RawDataDecisionInstanceDto {

  protected String decisionDefinitionKey;
  protected String decisionDefinitionId;
  protected String decisionInstanceId;
  protected OffsetDateTime evaluationDateTime;
  protected String engineName;
  protected Map<String, InputVariableEntry> inputVariables;
  protected Map<String, OutputVariableEntry> outputVariables;

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public void setDecisionDefinitionId(String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  public void setDecisionInstanceId(String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public OffsetDateTime getEvaluationDateTime() {
    return evaluationDateTime;
  }

  public void setEvaluationDateTime(OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  public Map<String, InputVariableEntry> getInputVariables() {
    return inputVariables;
  }

  public void setInputVariables(Map<String, InputVariableEntry> inputVariables) {
    this.inputVariables = inputVariables;
  }

  public Map<String, OutputVariableEntry> getOutputVariables() {
    return outputVariables;
  }

  public void setOutputVariables(final Map<String, OutputVariableEntry> outputVariables) {
    this.outputVariables = outputVariables;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RawDataDecisionInstanceDto)) {
      return false;
    }
    final RawDataDecisionInstanceDto that = (RawDataDecisionInstanceDto) o;
    return Objects.equals(decisionDefinitionKey, that.decisionDefinitionKey) &&
      Objects.equals(decisionDefinitionId, that.decisionDefinitionId) &&
      Objects.equals(decisionInstanceId, that.decisionInstanceId) &&
      Objects.equals(evaluationDateTime, that.evaluationDateTime) &&
      Objects.equals(engineName, that.engineName) &&
      Objects.equals(inputVariables, that.inputVariables) &&
      Objects.equals(outputVariables, that.outputVariables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      decisionDefinitionKey,
      decisionDefinitionId,
      decisionInstanceId,
      evaluationDateTime,
      engineName,
      inputVariables,
      outputVariables
    );
  }
}
