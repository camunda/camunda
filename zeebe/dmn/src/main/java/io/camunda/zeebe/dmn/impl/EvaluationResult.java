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

/** A successfully evaluated decision */
public final class EvaluationResult implements DecisionEvaluationResult {

  private final DirectBuffer output;
  private final List<EvaluatedDecision> evaluatedDecisions;

  public EvaluationResult(
      final DirectBuffer output, final List<EvaluatedDecision> evaluatedDecisions) {
    this.output = output;
    this.evaluatedDecisions = evaluatedDecisions;
  }

  @Override
  public boolean isFailure() {
    return false;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public String getFailedDecisionId() {
    return null;
  }

  @Override
  public DirectBuffer getOutput() {
    return output;
  }

  @Override
  public List<EvaluatedDecision> getEvaluatedDecisions() {
    return evaluatedDecisions;
  }
}
