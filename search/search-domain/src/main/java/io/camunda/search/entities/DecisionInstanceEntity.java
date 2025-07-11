/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;

public record DecisionInstanceEntity(
    String decisionInstanceId, // this is the unique identifier of the decision instance
    Long decisionInstanceKey,
    DecisionInstanceState state,
    OffsetDateTime evaluationDate,
    String evaluationFailure,
    String evaluationFailureMessage,
    Long processDefinitionKey,
    Long processInstanceKey,
    String tenantId,
    String decisionDefinitionId,
    Long decisionDefinitionKey,
    String decisionDefinitionName,
    Integer decisionDefinitionVersion,
    DecisionDefinitionType decisionDefinitionType,
    String result,
    List<DecisionInstanceInputEntity> evaluatedInputs,
    List<DecisionInstanceOutputEntity> evaluatedOutputs)
    implements TenantOwnedEntity {

  public Builder toBuilder() {
    return new Builder()
        .decisionInstanceId(decisionInstanceId)
        .decisionInstanceKey(decisionInstanceKey)
        .state(state)
        .evaluationDate(evaluationDate)
        .evaluationFailure(evaluationFailure)
        .evaluationFailureMessage(evaluationFailureMessage)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .decisionDefinitionKey(decisionDefinitionKey)
        .decisionDefinitionId(decisionDefinitionId)
        .decisionDefinitionName(decisionDefinitionName)
        .decisionDefinitionVersion(decisionDefinitionVersion)
        .decisionDefinitionType(decisionDefinitionType)
        .tenantId(tenantId)
        .result(result)
        .evaluatedInputs(evaluatedInputs)
        .evaluatedOutputs(evaluatedOutputs);
  }

  public static class Builder implements ObjectBuilder<DecisionInstanceEntity> {

    private String decisionInstanceId;
    private Long decisionInstanceKey;
    private DecisionInstanceState state;
    private OffsetDateTime evaluationDate;
    private String evaluationFailure;
    private String evaluationFailureMessage;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private String tenantId;
    private String decisionDefinitionId;
    private Long decisionDefinitionKey;
    private String decisionDefinitionName;
    private Integer decisionDefinitionVersion;
    private DecisionDefinitionType decisionDefinitionType;
    private String result;
    private List<DecisionInstanceInputEntity> evaluatedInputs;
    private List<DecisionInstanceOutputEntity> evaluatedOutputs;

    public Builder decisionInstanceId(final String decisionInstanceId) {
      this.decisionInstanceId = decisionInstanceId;
      return this;
    }

    public Builder decisionInstanceKey(final Long decisionInstanceKey) {
      this.decisionInstanceKey = decisionInstanceKey;
      return this;
    }

    public Builder state(final DecisionInstanceState state) {
      this.state = state;
      return this;
    }

    public Builder evaluationDate(final OffsetDateTime evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
    }

    public Builder evaluationFailure(final String evaluationFailure) {
      this.evaluationFailure = evaluationFailure;
      return this;
    }

    public Builder evaluationFailureMessage(final String evaluationFailureMessage) {
      this.evaluationFailureMessage = evaluationFailureMessage;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    public Builder decisionDefinitionKey(final Long decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public Builder decisionDefinitionName(final String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    public Builder decisionDefinitionVersion(final Integer decisionDefinitionVersion) {
      this.decisionDefinitionVersion = decisionDefinitionVersion;
      return this;
    }

    public Builder decisionDefinitionType(final DecisionDefinitionType decisionDefinitionType) {
      this.decisionDefinitionType = decisionDefinitionType;
      return this;
    }

    public Builder result(final String result) {
      this.result = result;
      return this;
    }

    public Builder evaluatedInputs(final List<DecisionInstanceInputEntity> evaluatedInputs) {
      this.evaluatedInputs = evaluatedInputs;
      return this;
    }

    public Builder evaluatedOutputs(final List<DecisionInstanceOutputEntity> evaluatedOutputs) {
      this.evaluatedOutputs = evaluatedOutputs;
      return this;
    }

    @Override
    public DecisionInstanceEntity build() {
      return new DecisionInstanceEntity(
          decisionInstanceId,
          decisionInstanceKey,
          state,
          evaluationDate,
          evaluationFailure,
          evaluationFailureMessage,
          processDefinitionKey,
          processInstanceKey,
          tenantId,
          decisionDefinitionId,
          decisionDefinitionKey,
          decisionDefinitionName,
          decisionDefinitionVersion,
          decisionDefinitionType,
          result,
          evaluatedInputs,
          evaluatedOutputs);
    }
  }

  public record DecisionInstanceInputEntity(String inputId, String inputName, String inputValue) {}

  public record DecisionInstanceOutputEntity(
      String outputId, String outputName, String outputValue, String ruleId, int ruleIndex) {}

  public enum DecisionDefinitionType {
    DECISION_TABLE,
    LITERAL_EXPRESSION,
    UNSPECIFIED,
    UNKNOWN;

    public static DecisionDefinitionType fromValue(final String value) {
      for (final DecisionDefinitionType b : DecisionDefinitionType.values()) {
        if (b.name().equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  public enum DecisionInstanceState {
    EVALUATED,
    FAILED,
    UNKNOWN,
    UNSPECIFIED;

    public static DecisionInstanceState fromValue(final String value) {
      for (final DecisionInstanceState b : DecisionInstanceState.values()) {
        if (b.name().equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
