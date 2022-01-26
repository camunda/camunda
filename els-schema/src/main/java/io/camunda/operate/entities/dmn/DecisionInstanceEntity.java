/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.entities.dmn;

import io.camunda.operate.entities.OperateZeebeEntity;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class DecisionInstanceEntity extends OperateZeebeEntity<DecisionInstanceEntity> {

  private DecisionInstanceState state;
  private OffsetDateTime evaluationTime;
  private Long position;
  private long decisionRequirementsKey;
  private String decisionRequirementsId;
  private long processDefinitionKey;
  private long processInstanceKey;
  private long elementInstanceKey;
  private String elementId;
  private String decisionId;
  private long decisionKey;
  private String decisionName;
  private DecisionType decisionType;
  private String result;
  private DesicionInstanceInputEntity[] evaluatedInputs;
  private DecisionInstanceOutputEntity[] evaluatedOutputs;

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

  public Long getPosition() {
    return position;
  }

  public DecisionInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public long getDecisionKey() {
    return decisionKey;
  }

  public DecisionInstanceEntity setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
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

  public DesicionInstanceInputEntity[] getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceEntity setEvaluatedInputs(
      final DesicionInstanceInputEntity[] evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public DecisionInstanceOutputEntity[] getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceEntity setEvaluatedOutputs(
      final DecisionInstanceOutputEntity[] evaluatedOutputs) {
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
        decisionKey == that.decisionKey &&
        state == that.state &&
        Objects.equals(evaluationTime, that.evaluationTime) &&
        Objects.equals(position, that.position) &&
        Objects.equals(decisionRequirementsId, that.decisionRequirementsId) &&
        Objects.equals(elementId, that.elementId) &&
        Objects.equals(decisionId, that.decisionId) &&
        Objects.equals(decisionName, that.decisionName) &&
        decisionType == that.decisionType &&
        Objects.equals(result, that.result) &&
        Arrays.equals(evaluatedInputs, that.evaluatedInputs) &&
        Arrays.equals(evaluatedOutputs, that.evaluatedOutputs);
  }

  @Override
  public int hashCode() {
    int result1 = Objects
        .hash(super.hashCode(), state, evaluationTime, position, decisionRequirementsKey,
            decisionRequirementsId, processDefinitionKey, processInstanceKey, elementInstanceKey,
            elementId, decisionId, decisionKey, decisionName, decisionType, result);
    result1 = 31 * result1 + Arrays.hashCode(evaluatedInputs);
    result1 = 31 * result1 + Arrays.hashCode(evaluatedOutputs);
    return result1;
  }
}
