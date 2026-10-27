/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

public class DecisionEvaluationInstanceKeyGenerator {

  private static final String ID_FORMAT = "%d-%d";

  private final long decisionEvaluationKey;
  private int index;

  public DecisionEvaluationInstanceKeyGenerator(final long decisionEvaluationKey) {
    this.decisionEvaluationKey = decisionEvaluationKey;
    index = 0;
  }

  public String generateKey(final int index) {
    // The decisionEvaluationInstanceKey is used to uniquely identify the evaluation of a
    // decision within a decision evaluation event. It is constructed by appending the decision
    // evaluation key to the index of the evaluated decision (starting from 1).
    return String.format(ID_FORMAT, decisionEvaluationKey, index);
  }

  public String next() {
    return generateKey(++index);
  }
}
