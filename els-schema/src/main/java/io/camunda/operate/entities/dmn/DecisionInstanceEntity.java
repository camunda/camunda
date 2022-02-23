/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.entities.dmn;

import io.camunda.operate.entities.OperateZeebeEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceEntity extends OperateZeebeEntity<DecisionInstanceEntity> {

  private DecisionInstanceState state;
  private OffsetDateTime evaluationTime;
  private String evaluationFailure;
  private Long position;
  private long decisionRequirementsKey;
  private String decisionRequirementsId;
  private long processDefinitionKey;
  private long processInstanceKey;
  private long elementInstanceKey;
  private String elementId;
  private String decisionId;
  private String decisionDefinitionId;
  private String decisionName;
  private DecisionType decisionType;
  private String result;
  private List<DecisionInstanceInputEntity> evaluatedInputs = new ArrayList<>();
  private List<DecisionInstanceOutputEntity> evaluatedOutputs = new ArrayList<>();

  public DecisionInstanceState getState() {
    return state;
  }

  public DecisionInstanceEntity setState(
      final DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getEvaluationTime() {
    return evaluationTime;
  }

  public DecisionInstanceEntity setEvaluationTime(final OffsetDateTime evaluationTime) {
    this.evaluationTime = evaluationTime;
    return this;
  }

  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  public DecisionInstanceEntity setEvaluationFailure(final String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public DecisionInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceEntity setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionInstanceEntity setDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionInstanceEntity setDecisionRequirementsId(
      final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstanceEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionInstanceEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public DecisionInstanceEntity setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public DecisionInstanceEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstanceEntity setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceEntity setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstanceEntity setDecisionType(
      final DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstanceEntity setResult(final String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInputEntity> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceEntity setEvaluatedInputs(
      final List<DecisionInstanceInputEntity> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutputEntity> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceEntity setEvaluatedOutputs(
      final List<DecisionInstanceOutputEntity> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
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
    final DecisionInstanceEntity that = (DecisionInstanceEntity) o;
    return decisionRequirementsKey == that.decisionRequirementsKey &&
        processDefinitionKey == that.processDefinitionKey &&
        processInstanceKey == that.processInstanceKey &&
        elementInstanceKey == that.elementInstanceKey &&
        state == that.state &&
        Objects.equals(evaluationTime, that.evaluationTime) &&
        Objects.equals(evaluationFailure, that.evaluationFailure) &&
        Objects.equals(position, that.position) &&
        Objects.equals(decisionRequirementsId, that.decisionRequirementsId) &&
        Objects.equals(elementId, that.elementId) &&
        Objects.equals(decisionId, that.decisionId) &&
        Objects.equals(decisionDefinitionId, that.decisionDefinitionId) &&
        Objects.equals(decisionName, that.decisionName) &&
        decisionType == that.decisionType &&
        Objects.equals(result, that.result) &&
        Objects.equals(evaluatedInputs, that.evaluatedInputs) &&
        Objects.equals(evaluatedOutputs, that.evaluatedOutputs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), state, evaluationTime, evaluationFailure, position,
        decisionRequirementsKey, decisionRequirementsId, processDefinitionKey, processInstanceKey,
        elementInstanceKey, elementId, decisionId, decisionDefinitionId, decisionName, decisionType,
        result, evaluatedInputs, evaluatedOutputs);
  }
}
