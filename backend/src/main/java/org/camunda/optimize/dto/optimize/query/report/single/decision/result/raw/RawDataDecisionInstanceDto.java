package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import com.google.common.base.Objects;

import java.time.OffsetDateTime;
import java.util.Map;

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
    return Objects.equal(decisionDefinitionKey, that.decisionDefinitionKey) &&
      Objects.equal(decisionDefinitionId, that.decisionDefinitionId) &&
      Objects.equal(decisionInstanceId, that.decisionInstanceId) &&
      Objects.equal(evaluationDateTime, that.evaluationDateTime) &&
      Objects.equal(engineName, that.engineName) &&
      Objects.equal(inputVariables, that.inputVariables) &&
      Objects.equal(outputVariables, that.outputVariables);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
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
