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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import java.util.Optional;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

public class DecisionInstanceAssertj
    extends AbstractAssert<DecisionInstanceAssertj, DecisionSelector>
    implements DecisionInstanceAssert {

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;
  private final DecisionMatchedRulesAssertj decisionMatchedRulesAssertj;
  private final DecisionOutputAssertj decisionOutputAssertj;

  public DecisionInstanceAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final DecisionSelector decisionSelector) {

    super(decisionSelector, DecisionInstanceAssert.class);

    final String failureMessagePrefix =
        String.format("Expected DecisionInstance [%s]", actual.describe());

    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
    decisionMatchedRulesAssertj = new DecisionMatchedRulesAssertj(failureMessagePrefix);
    decisionOutputAssertj = new DecisionOutputAssertj(failureMessagePrefix);
  }

  @Override
  public DecisionInstanceAssert isEvaluated() {
    awaitDecisionInstance(
        instance ->
            assertThat(instance.getState())
                .withFailMessage(
                    "Expected DecisionInstance [%s] to have been evaluated, but was %s",
                    actual.describe(), formatState(instance.getState()))
                .isEqualTo(DecisionInstanceState.EVALUATED));
    return this;
  }

  @Override
  public DecisionInstanceAssert hasOutput(final Object expectedOutput) {
    awaitDecisionInstance(
        instance -> decisionOutputAssertj.hasOutput(instance.getResult(), expectedOutput));

    return this;
  }

  @Override
  public DecisionInstanceAssert hasNoMatchedRules() {
    awaitDecisionInstance(
        instance -> decisionMatchedRulesAssertj.hasNoMatchedRules(instance.getMatchedRules()));

    return this;
  }

  @Override
  public DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndexes) {
    awaitDecisionInstance(
        instance ->
            decisionMatchedRulesAssertj.hasMatchedRules(
                instance.getMatchedRules(), expectedMatchedRuleIndexes));

    return this;
  }

  @Override
  public DecisionInstanceAssert hasNotMatchedRules(final int... expectedUnmatchedRuleIndexes) {
    awaitDecisionInstance(
        instance ->
            decisionMatchedRulesAssertj.hasNotMatchedRules(
                instance.getMatchedRules(), expectedUnmatchedRuleIndexes));

    return this;
  }

  private String formatState(final DecisionInstanceState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private void awaitDecisionInstance(final Consumer<DecisionInstance> assertion) {
    awaitBehavior.untilAsserted(
        () -> dataSource.findDecisionInstances(actual::applyFilter),
        decisionInstances -> {
          final Optional<DecisionInstance> discoveredDecisionInstance =
              decisionInstances.stream().filter(actual::test).findFirst();

          assertThat(discoveredDecisionInstance)
              .withFailMessage("No DecisionInstance [%s] found.", actual.describe())
              .isPresent();
          // We need to use the getById endpoint because only that endpoint contains
          // the matchedRules() and evaluatedInput data.
          final DecisionInstance completeDecisionInstance =
              dataSource.getDecisionInstance(
                  discoveredDecisionInstance.get().getDecisionInstanceId());

          assertion.accept(completeDecisionInstance);
        });
  }
}
