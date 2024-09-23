/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

public record DecisionInstanceEntity(
    long key,
    DecisionInstanceState state,
    String evaluationDate,
    String evaluationFailure,
    long processDefinitionKey,
    long processInstanceKey,
    String bpmnProcessId,
    String decisionId,
    String decisionDefinitionId,
    String decisionName,
    int decisionVersion,
    DecisionInstanceType decisionType,
    String result) {

  public record DecisionInstanceInputEntity(String id, String name, String value) {}

  public record DecisionInstanceOutputEntity(
      String id, String name, String value, String ruleId, int ruleIndex) {}

  public enum DecisionInstanceType {
    DECISION_TABLE,
    LITERAL_EXPRESSION,
    UNSPECIFIED,
    UNKNOWN;

    public static DecisionInstanceType fromValue(String value) {
      for (DecisionInstanceType b : DecisionInstanceType.values()) {
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

    public static DecisionInstanceState fromValue(String value) {
      for (DecisionInstanceState b : DecisionInstanceState.values()) {
        if (b.name().equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
