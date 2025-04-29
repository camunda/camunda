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
import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class DecisionInstanceAssertj
    extends AbstractAssert<DecisionInstanceAssertj, DecisionSelector>
    implements DecisionInstanceAssert {

  private final CamundaDataSource dataSource;

  public DecisionInstanceAssertj(
      final CamundaDataSource dataSource, final DecisionSelector decisionSelector) {

    super(decisionSelector, DecisionInstanceAssert.class);
    this.dataSource = dataSource;
  }

  @Override
  public DecisionInstanceAssert isEvaluated() {
    awaitDecisionInstance(
        instance ->
            assertThat(instance.getState())
                .withFailMessage(
                    "Expected [%s] to have been evaluated, but was %s",
                    actual.describe(), formatState(instance.getState()))
                .isEqualTo(DecisionInstanceState.EVALUATED));
    return this;
  }

  @Override
  public DecisionInstanceAssert hasOutput(final Object expectedOutput) {
    awaitDecisionInstance(
        instance -> {
          try {
            final Object result =
                dataSource.getJsonMapper().fromJson(instance.getResult(), Object.class);

            assertThat(result)
                .withFailMessage(
                    "Expected [%s] to have output '%s', but was '%s'",
                    actual.describe(), expectedOutput, formatResult(instance.getResult()))
                .isEqualTo(expectedOutput);
          } catch (final ClientException | IllegalArgumentException e) {
            // instance.getResult() could not be deserialized.
            fail(
                "Expected [%s] to have output '%s', but was '%s'",
                actual.describe(), expectedOutput, formatResult(instance.getResult()));
          }
        });

    return this;
  }

  @Override
  public DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndexes) {
    final List<Integer> expectedMatches =
        Arrays.stream(expectedMatchedRuleIndexes).boxed().collect(Collectors.toList());

    awaitDecisionInstance(
        instance -> {
          final List<Integer> actualMatchedRuleIndices =
              instance.getMatchedRules().stream()
                  .map(MatchedDecisionRule::getRuleIndex)
                  .collect(Collectors.toList());

          assertThat(actualMatchedRuleIndices)
              .withFailMessage(
                  "Expected [%s] to have matched rules %s, but did not. Matches:\n"
                      + "\t- matched: %s\n"
                      + "\t- missing: %s\n"
                      + "\t- unexpected: %s",
                  actual.describe(),
                  Arrays.toString(expectedMatchedRuleIndexes),
                  matchingRules(actualMatchedRuleIndices, expectedMatches),
                  missingRules(actualMatchedRuleIndices, expectedMatches),
                  unexpectedRules(actualMatchedRuleIndices, expectedMatches))
              .containsAll(expectedMatches);
        });

    return this;
  }

  @Override
  public DecisionInstanceAssert hasNotMatchedRules(final int... expectedUnmatchedRuleIndexes) {
    final List<Integer> expectedUnmatchedRules =
        Arrays.stream(expectedUnmatchedRuleIndexes).boxed().collect(Collectors.toList());

    awaitDecisionInstance(
        instance -> {
          final List<Integer> actualMatchedRuleIndices =
              instance.getMatchedRules().stream()
                  .map(MatchedDecisionRule::getRuleIndex)
                  .collect(Collectors.toList());

          assertThat(actualMatchedRuleIndices)
              .withFailMessage(
                  "Expected [%s] to not have matched rules %s, but matched %s",
                  actual.describe(),
                  Arrays.toString(expectedUnmatchedRuleIndexes),
                  matchingRules(actualMatchedRuleIndices, expectedUnmatchedRules))
              .doesNotContainAnyElementsOf(expectedUnmatchedRules);
        });

    return this;
  }

  private <T> List<T> matchingRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return expectedMatches.stream().filter(actualMatches::contains).collect(Collectors.toList());
  }

  private <T> List<T> missingRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return expectedMatches.stream()
        .filter(e -> actualMatches.stream().noneMatch(a -> Objects.equals(a, e)))
        .collect(Collectors.toList());
  }

  private <T> List<T> unexpectedRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return actualMatches.stream()
        .filter(e -> expectedMatches.stream().noneMatch(a -> Objects.equals(a, e)))
        .collect(Collectors.toList());
  }

  private String formatState(final DecisionInstanceState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private Object formatResult(final String result) {
    return dataSource.getJsonMapper().fromJson(result, Object.class);
  }

  private void awaitDecisionInstance(final Consumer<DecisionInstance> assertion) {
    final AtomicReference<String> failureMessage = new AtomicReference<>("?");

    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findDecisionInstances(actual::applyFilter),
              decisionInstances -> {
                final Optional<DecisionInstance> discoveredDecisionInstance =
                    decisionInstances.stream().filter(actual::test).findFirst();

                try {
                  assertThat(discoveredDecisionInstance)
                      .withFailMessage("No decision instance [%s] found.", actual.describe())
                      .isPresent();
                  // We need to use the getById endpoint because only that endpoint contains
                  // the matchedRules() and evaluatedInput data.
                  final DecisionInstance completeDecisionInstance =
                      dataSource.getDecisionInstance(
                          discoveredDecisionInstance.get().getDecisionInstanceId());

                  assertion.accept(completeDecisionInstance);
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });
    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }
}
