/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
   * Verifies that the decision is evaluated with the expected output. The verification fails if the
   * decision is not evaluated or the output does not match.
   *
   * @param expectedOutput the expected output value
   * @return the assertion object
   */
  DecisionInstanceAssert hasOutput(final Object expectedOutput);

  /**
   * Verifies that the decision has matched the given rule indices.
   *
   * @param expectedMatchedRuleIndexes the rule indices that should have matched
   * @return the assertion object
   */
  DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndexes);

  /**
   * Verifies that the decision has not matched the given rule indices.
   *
   * @param expectedUnmatchedRuleIndexes the rule indices that should not have matched
   * @return the assertion object
   */
  DecisionInstanceAssert hasNotMatchedRules(final int... expectedUnmatchedRuleIndexes);
}
