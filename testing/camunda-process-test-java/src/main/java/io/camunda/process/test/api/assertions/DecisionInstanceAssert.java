/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.assertions;

/** The assertion object to verify a decision. */
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
   * Verifies that the decision has the given name.
   *
   * @param expectedDefinitionName the expected name for this decision
   * @return the assertion object
   */
  DecisionInstanceAssert hasName(final String expectedDefinitionName);

  /**
   * Verifies that the definition has the given definition id.
   *
   * @param expectedDefinitionId the expected id for this decision
   * @return the assertion object
   */
  DecisionInstanceAssert hasId(final String expectedDefinitionId);

  /**
   * Verifies that the definition has the given version.
   *
   * @param expectedVersion the expected version for this decision
   * @return the assertion object
   */
  DecisionInstanceAssert hasVersion(final int expectedVersion);

  /**
   * Verifies that the decision is evaluated with the expected output. The verification fails if the
   * decision is not evaluated or the output does not match.
   *
   * @param expectedOutputValue the expected output value
   * @return the assertion object
   */
  DecisionInstanceAssert hasOutput(String expectedOutputValue);

  /**
   * Verifies that the decision has matched the given rule indices.
   *
   * @param expectedMatchedRuleIndices the rule indices that should have matched
   * @return the assertion object
   */
  DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndices);
}
