/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.assertions;

/** The assertion object to verify a decision.*/
public interface DecisionInstanceAssert {
  /**
   * Verifies that the decision is evaluated. The verification fails if the decision is not
   * evaluated or if the evaluation fails.
   *
   * <p>The assertion waits until the decision is evaluated.
   *
   * @return the assertion object
   */
  DecisionInstanceAssert isEvaluated();

  /**
   * Verifies that the decision evaluation has failed.
   *
   * <p>The assertion waits until the decision is evaluated.
   *
   * @return the assertion object
   */
  DecisionInstanceAssert isFailed();

  /**
   * Verifies that the decision is evaluated with the expected output. The verification fails if
   * the decision is not evaluated or the output does not match.
   *
   * @param expectedOutputValue the expected output value
   * @return the assertion object
   */
  DecisionInstanceAssert hasOutput(String expectedOutputValue);

  /**
   * Verifies that the decision is evaluated with the expected output. The verification fails if
   * the decision failed or the output does not match.
   *
   * @param expectedOutputName the expected output variable name
   * @param expectedOutputValue the expected output value
   * @return the assertion object
   */
  DecisionInstanceAssert hasOutput(final String expectedOutputName, final String expectedOutputValue);

  /**
   * Verifies that the decision is evaluated with the expected output. The verification fails if
   * the decision is not evaluated or the output does not match.
   *
   * @param a
   * @param b
   * @return
   */
  DecisionInstanceAssert hasMatchedRules(int a, int b);
}
