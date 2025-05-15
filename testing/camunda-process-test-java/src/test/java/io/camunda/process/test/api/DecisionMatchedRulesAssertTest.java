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
package io.camunda.process.test.api;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.api.response.EvaluatedDecision;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.impl.response.EvaluatedDecisionImpl;
import io.camunda.client.impl.response.MatchedDecisionRuleImpl;
import io.camunda.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.client.protocol.rest.EvaluatedDecisionResult;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionMatchedRulesAssertTest {

  private static final String DECISION_DEFINITION_KEY = "4";
  private static final int DECISION_DEFINITION_VERSION = 1;

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  private static Stream<Arguments> positiveMatchedRuleScenarios() {
    return Stream.of(
        Arguments.of(evaluatedDecision(decisionResult(singleRule())), new int[] {1}),
        Arguments.of(evaluatedDecision(decisionResult(singleRule())), new int[] {1, 1, 1}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {1, 2, 3}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {3, 2, 1}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {1, 2}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {2, 3}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {1, 3}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {1}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {2}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {3}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {1, 1, 1}));
  }

  private static Stream<Arguments> positiveUnmatchedRuleScenarios() {
    return Stream.of(
        Arguments.of(evaluatedDecision(decisionResult(singleRule())), new int[] {0}),
        Arguments.of(evaluatedDecision(decisionResult(singleRule())), new int[] {2}),
        Arguments.of(evaluatedDecision(decisionResult(singleRule())), new int[] {0, 0}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {0}),
        Arguments.of(evaluatedDecision(decisionResult(multiRule())), new int[] {4, 5, 6}));
  }

  private static EvaluatedDecision evaluatedDecision(EvaluatedDecisionResult decisionResult) {
    return new EvaluatedDecisionImpl(decisionResult, null);
  }

  private static EvaluatedDecisionResult decisionResult(final MatchedDecisionRuleItem... rules) {
    final List<MatchedDecisionRuleItem> rulesList =
        Arrays.stream(rules).collect(Collectors.toList());

    return new EvaluatedDecisionResult()
        .decisionDefinitionId("name")
        .decisionDefinitionKey(DECISION_DEFINITION_KEY)
        .decisionDefinitionVersion(DECISION_DEFINITION_VERSION)
        .matchedRules(rulesList)
        .output("result");
  }

  private static MatchedDecisionRuleItem singleRule() {
    return new MatchedDecisionRuleItem()
        .ruleId("ruleId")
        .ruleIndex(1)
        .addEvaluatedOutputsItem(
            new EvaluatedDecisionOutputItem()
                .outputId("outputId")
                .outputName("outputName")
                .outputValue("outputValue"));
  }

  private static MatchedDecisionRuleItem[] multiRule() {
    return new MatchedDecisionRuleItem[] {
      new MatchedDecisionRuleItem()
          .ruleId("ruleId1")
          .ruleIndex(1)
          .addEvaluatedOutputsItem(
              new EvaluatedDecisionOutputItem()
                  .outputId("outputId1")
                  .outputName("outputName1")
                  .outputValue("outputValue1")),
      new MatchedDecisionRuleItem()
          .ruleId("ruleId2")
          .ruleIndex(2)
          .addEvaluatedOutputsItem(
              new EvaluatedDecisionOutputItem()
                  .outputId("outputId2")
                  .outputName("outputName2")
                  .outputValue("outputValue2")),
      new MatchedDecisionRuleItem()
          .ruleId("ruleId3")
          .ruleIndex(3)
          .addEvaluatedOutputsItem(
              new EvaluatedDecisionOutputItem()
                  .outputId("outputId3")
                  .outputName("outputName3")
                  .outputValue("outputValue3"))
    };
  }

  @Nested
  class HasMatchedRules {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.process.test.api.DecisionMatchedRulesAssertTest#positiveMatchedRuleScenarios")
    void hasMatchedRules(final EvaluatedDecision decision, final int[] expectedMatchedRules) {
      // then
      CamundaAssert.assertThat(decision).hasMatchedRules(expectedMatchedRules);
    }

    @Test
    void hasDetailedOutputIfNoMatchesFound() {
      // then
      final EvaluatedDecision decision = evaluatedDecision(decisionResult(singleRule()));
      Assertions.assertThatThrownBy(() -> assertThat(decision).hasMatchedRules(2))
          .hasMessage(
              "Expected EvaluatedDecision [name] to have matched rules [2], but did not. Matches:\n"
                  + "\t- matched: []\n"
                  + "\t- missing: [2]\n"
                  + "\t- unexpected: [1]");
    }

    @Test
    void hasDetailedOutputIfPartialMatchesFound() {
      // then
      final EvaluatedDecision decision = evaluatedDecision(decisionResult(multiRule()));
      Assertions.assertThatThrownBy(() -> assertThat(decision).hasMatchedRules(1, 2, 4))
          .hasMessage(
              "Expected EvaluatedDecision [name] to have matched rules [1, 2, 4], but did not. Matches:\n"
                  + "\t- matched: [1, 2]\n"
                  + "\t- missing: [4]\n"
                  + "\t- unexpected: [3]");
    }
  }

  @Nested
  class HasNotMatchedRules {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.process.test.api.DecisionMatchedRulesAssertTest#positiveUnmatchedRuleScenarios")
    void hasMatchedRules(final EvaluatedDecision decision, final int[] expectedMatchedRules) {
      // then
      CamundaAssert.assertThat(decision).hasNotMatchedRules(expectedMatchedRules);
    }

    @Test
    void hasDetailedOutputIfMatched() {
      // then
      final EvaluatedDecision decision = evaluatedDecision(decisionResult(singleRule()));
      Assertions.assertThatThrownBy(() -> assertThat(decision).hasNotMatchedRules(1))
          .hasMessage(
              "Expected EvaluatedDecision [name] to not have matched rules [1], but matched [1]");
    }

    @Test
    void hasDetailedOutputIfPartiallyMatched() {
      // then
      final EvaluatedDecision decision = evaluatedDecision(decisionResult(multiRule()));
      Assertions.assertThatThrownBy(() -> assertThat(decision).hasNotMatchedRules(1, 2, 4))
          .hasMessage(
              "Expected EvaluatedDecision [name] to not have matched rules [1, 2, 4], but matched [1, 2]");
    }
  }
}
