/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.DecisionResult;
import org.agrona.DirectBuffer;

/** A successfully evaluated decision */
public final class EvaluatedDecision implements DecisionResult {

  private final DirectBuffer output;

  public EvaluatedDecision(final DirectBuffer output) {
    this.output = output;
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
  public DirectBuffer getOutput() {
    return output;
  }
}
