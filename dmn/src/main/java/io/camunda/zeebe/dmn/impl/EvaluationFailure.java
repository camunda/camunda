/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import java.util.List;
import org.agrona.DirectBuffer;

/** The result of an unsuccessful attempt to evaluate a decision. */
public final class EvaluationFailure implements DecisionEvaluationResult {

  private final String message;
  private final String failedDecisionId;
  private final List<EvaluatedDecision> evaluatedDecisions;

  public EvaluationFailure(final String message, final String failedDecisionId) {
    this(message, failedDecisionId, List.of());
  }

  public EvaluationFailure(
      final String message,
      final String failedDecisionId,
      final List<EvaluatedDecision> evaluatedDecisions) {
    this.message = message;
    this.failedDecisionId = failedDecisionId;
    this.evaluatedDecisions = evaluatedDecisions;
  }

  @Override
  public boolean isFailure() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return message;
  }

  @Override
  public String getFailedDecisionId() {
    return failedDecisionId;
  }

  @Override
  public DirectBuffer getOutput() {
    return null;
  }

  @Override
  public List<EvaluatedDecision> getEvaluatedDecisions() {
    return evaluatedDecisions;
  }
}
