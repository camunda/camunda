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

/** The result of an unsuccessful attempt to evaluate a decision. */
public final class EvaluationFailure implements DecisionResult {

  private final String message;

  public EvaluationFailure(final String message) {
    this.message = message;
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
  public DirectBuffer getOutput() {
    return null;
  }
}
