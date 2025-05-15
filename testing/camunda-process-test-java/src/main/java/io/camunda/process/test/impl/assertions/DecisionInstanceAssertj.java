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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import java.util.List;
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
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final DecisionMatchedRulesAssertj decisionMatchedRulesAssertj;

  public DecisionInstanceAssertj(
      final CamundaDataSource dataSource, final DecisionSelector decisionSelector) {

    super(decisionSelector, DecisionInstanceAssert.class);

    this.dataSource = dataSource;
    this.decisionMatchedRulesAssertj =
        new DecisionMatchedRulesAssertj(
            String.format("Expected DecisionInstance [%s]", actual.describe()));
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
        instance -> {
          try {
            final JsonNode result = readJson(instance.getResult());
            final JsonNode expectedOutputJson = toJson(expectedOutput);

            assertThat(result)
                .withFailMessage(
                    "Expected DecisionInstance [%s] to have output '%s', but was '%s'",
                    actual.describe(), expectedOutput, readJson(instance.getResult()))
                .isEqualTo(expectedOutputJson);
          } catch (final ClientException | IllegalArgumentException e) {
            // instance.getResult() could not be deserialized.
            fail(
                "Expected DecisionInstance [%s] to have output '%s', but was '%s'",
                actual.describe(), expectedOutput, readJson(instance.getResult()));
          }
        });

    return this;
  }

  @Override
  public DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndexes) {
    awaitDecisionInstance(
        instance -> {
          final List<Integer> actualMatchedRuleIndices =
              instance.getMatchedRules().stream()
                  .map(MatchedDecisionRule::getRuleIndex)
                  .collect(Collectors.toList());

          decisionMatchedRulesAssertj.hasMatchedRules(
              actualMatchedRuleIndices, expectedMatchedRuleIndexes);
        });

    return this;
  }

  @Override
  public DecisionInstanceAssert hasNotMatchedRules(final int... expectedUnmatchedRuleIndexes) {
    awaitDecisionInstance(
        instance -> {
          final List<Integer> actualMatchedRuleIndices =
              instance.getMatchedRules().stream()
                  .map(MatchedDecisionRule::getRuleIndex)
                  .collect(Collectors.toList());

          decisionMatchedRulesAssertj.hasNotMatchedRules(
              actualMatchedRuleIndices, expectedUnmatchedRuleIndexes);
        });

    return this;
  }

  private String formatState(final DecisionInstanceState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
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
                      .withFailMessage("No DecisionInstance [%s] found.", actual.describe())
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

  private JsonNode readJson(final String value) {
    if (value == null) {
      return NullNode.getInstance();
    }

    try {
      return jsonMapper.readValue(value, JsonNode.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  private JsonNode toJson(final Object value) {
    try {
      return jsonMapper.convertValue(value, JsonNode.class);
    } catch (final IllegalArgumentException e) {
      throw new RuntimeException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }
}
