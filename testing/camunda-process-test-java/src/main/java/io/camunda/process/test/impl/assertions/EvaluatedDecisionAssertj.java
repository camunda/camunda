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
package io.camunda.process.test.impl.assertions;

import io.camunda.client.api.response.EvaluatedDecision;
import io.camunda.process.test.api.assertions.EvaluatedDecisionAssert;
import org.assertj.core.api.AbstractAssert;

public class EvaluatedDecisionAssertj
    extends AbstractAssert<EvaluatedDecisionAssertj, EvaluatedDecision>
    implements EvaluatedDecisionAssert {

  private final DecisionMatchedRulesAssertj decisionMatchedRulesAssertj;
  private final DecisionOutputAssertj decisionOutputAssertj;

  public EvaluatedDecisionAssertj(final EvaluatedDecision evaluatedDecision) {
    super(evaluatedDecision, EvaluatedDecisionAssert.class);

    final String failureMessagePrefix =
        String.format("Expected EvaluatedDecision [%s]", evaluatedDecision.getDecisionId());

    this.decisionMatchedRulesAssertj = new DecisionMatchedRulesAssertj(failureMessagePrefix);
    this.decisionOutputAssertj = new DecisionOutputAssertj(failureMessagePrefix);
  }

  @Override
  public EvaluatedDecisionAssert hasOutput(final Object expectedOutput) {
    decisionOutputAssertj.hasOutput(actual.getDecisionOutput(), expectedOutput);
    return this;
  }

  @Override
  public EvaluatedDecisionAssert hasMatchedRules(final int... expectedMatchedRuleIndexes) {
    this.decisionMatchedRulesAssertj.hasMatchedRules(
        actual.getMatchedRules(), expectedMatchedRuleIndexes);
    return this;
  }

  @Override
  public EvaluatedDecisionAssert hasNotMatchedRules(final int... expectedUnmatchedRuleIndexes) {
    this.decisionMatchedRulesAssertj.hasNotMatchedRules(
        actual.getMatchedRules(), expectedUnmatchedRuleIndexes);
    return this;
  }
}
