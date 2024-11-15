/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import java.time.OffsetDateTime;
import java.util.List;

public record DecisionInstanceEntity(
    Long decisionInstanceKey,
    String decisionInstanceId,
    DecisionInstanceState state,
    OffsetDateTime evaluationDate,
    String evaluationFailure,
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
    List<DecisionInstanceOutputEntity> evaluatedOutputs) {

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
