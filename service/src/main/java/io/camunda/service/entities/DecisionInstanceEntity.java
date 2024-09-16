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
    long elementInstanceKey,
    String elementId,
    String decisionId,
    String decisionDefinitionId,
    String decisionName,
    int decisionVersion,
    DecisionType decisionType,
    String result) {

  public record DecisionInstanceInputEntity(String id, String name, String value) {}

  public record DecisionInstanceOutputEntity(
      String id, String name, String value, String ruleId, int ruleIndex) {}

  public enum DecisionType {
    DECISION_TABLE,
    LITERAL_EXPRESSION,
    UNSPECIFIED,
    UNKNOWN;
  }

  public enum DecisionInstanceState {
    EVALUATED,
    FAILED,
    UNKNOWN,
    UNSPECIFIED;
  }
}
