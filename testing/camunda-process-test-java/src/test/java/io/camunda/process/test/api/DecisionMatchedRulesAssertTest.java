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

import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.impl.response.MatchedDecisionRuleImpl;
import io.camunda.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.process.test.impl.assertions.DecisionMatchedRulesAssertj;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionMatchedRulesAssertTest {

  private final DecisionMatchedRulesAssertj asserter =
      new DecisionMatchedRulesAssertj("Expected EvaluatedDecision [name]");

  private static Stream<Arguments> positiveMatchedRuleScenarios() {
    return Stream.of(
        Arguments.of(singleRule(), new int[] {1}),
        Arguments.of(singleRule(), new int[] {1, 1, 1}),
        Arguments.of(multiRule(), new int[] {1, 2, 3}),
        Arguments.of(multiRule(), new int[] {3, 2, 1}),
        Arguments.of(multiRule(), new int[] {1, 2}),
        Arguments.of(multiRule(), new int[] {2, 3}),
        Arguments.of(multiRule(), new int[] {1, 3}),
        Arguments.of(multiRule(), new int[] {1}),
        Arguments.of(multiRule(), new int[] {2}),
        Arguments.of(multiRule(), new int[] {3}),
        Arguments.of(multiRule(), new int[] {1, 1, 1}));
  }

  private static Stream<Arguments> positiveUnmatchedRuleScenarios() {
    return Stream.of(
        Arguments.of(singleRule(), new int[] {3}),
        Arguments.of(singleRule(), new int[] {2}),
        Arguments.of(singleRule(), new int[] {2, 2}),
        Arguments.of(multiRule(), new int[] {4}),
        Arguments.of(multiRule(), new int[] {4, 5, 6}));
  }

  private static List<MatchedDecisionRule> singleRule() {
    final List<MatchedDecisionRule> matchedDecisionRuleItems = new java.util.ArrayList<>();
    matchedDecisionRuleItems.add(
        new MatchedDecisionRuleImpl(
            new MatchedDecisionRuleItem()
                .ruleId("ruleId")
                .ruleIndex(1)
                .addEvaluatedOutputsItem(
                    new EvaluatedDecisionOutputItem()
                        .outputId("outputId")
                        .outputName("outputName")
                        .outputValue("outputValue")),
            null));
    return matchedDecisionRuleItems;
  }

  private static List<MatchedDecisionRule> multiRule() {
    final List<MatchedDecisionRule> matchedDecisionRuleItems = new java.util.ArrayList<>();
    matchedDecisionRuleItems.add(
        new MatchedDecisionRuleImpl(
            new MatchedDecisionRuleItem()
                .ruleId("ruleId")
                .ruleIndex(1)
                .addEvaluatedOutputsItem(
                    new EvaluatedDecisionOutputItem()
                        .outputId("outputId1")
                        .outputName("outputName1")
                        .outputValue("outputValue1")),
            null));
    matchedDecisionRuleItems.add(
        new MatchedDecisionRuleImpl(
            new MatchedDecisionRuleItem()
                .ruleId("ruleId")
                .ruleIndex(2)
                .addEvaluatedOutputsItem(
                    new EvaluatedDecisionOutputItem()
                        .outputId("outputId2")
                        .outputName("outputName2")
                        .outputValue("outputValue2")),
            null));
    matchedDecisionRuleItems.add(
        new MatchedDecisionRuleImpl(
            new MatchedDecisionRuleItem()
                .ruleId("ruleId")
                .ruleIndex(3)
                .addEvaluatedOutputsItem(
                    new EvaluatedDecisionOutputItem()
                        .outputId("outputId3")
                        .outputName("outputName3")
                        .outputValue("outputValue3")),
            null));
    return matchedDecisionRuleItems;
  }

  @Nested
  class HasMatchedRules {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.process.test.api.DecisionMatchedRulesAssertTest#positiveMatchedRuleScenarios")
    void hasMatchedRules(
        final List<MatchedDecisionRule> matchedRules, final int[] expectedMatchedRules) {
      // then
      asserter.hasMatchedRules(matchedRules, expectedMatchedRules);
    }

    @Test
    void hasDetailedOutputIfNoMatchesFound() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(singleRule(), 2))
          .hasMessage(
              "Expected EvaluatedDecision [name] to have matched rules [2], but did not. Matches:\n"
                  + "\t- matched: []\n"
                  + "\t- missing: [2]\n"
                  + "\t- unexpected: [1]");
    }

    @Test
    void hasDetailedOutputIfPartialMatchesFound() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(multiRule(), 1, 2, 4))
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
    void hasNotMatchedRules(
        final List<MatchedDecisionRule> matchedRules, final int[] expectedMatchedRules) {
      // then
      asserter.hasNotMatchedRules(matchedRules, expectedMatchedRules);
    }

    @Test
    void hasDetailedOutputIfMatched() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasNotMatchedRules(singleRule(), 1))
          .hasMessage(
              "Expected EvaluatedDecision [name] to not have matched rules [1], but matched [1]");
    }

    @Test
    void hasDetailedOutputIfPartiallyMatched() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasNotMatchedRules(multiRule(), 1, 2, 4))
          .hasMessage(
              "Expected EvaluatedDecision [name] to not have matched rules [1, 2, 4], but matched [1, 2]");
    }
  }

  @Nested
  class HasNoMatchedRules {

    @Test
    void hasNoMatchedRules() {
      // then
      asserter.hasNoMatchedRules(Collections.emptyList());
    }

    @Test
    void hasMatches() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasNoMatchedRules(multiRule()))
          .hasMessage(
              "Expected EvaluatedDecision [name] to have no matches, but matched [1, 2, 3]");
    }
  }

  @Nested
  class EdgeCases {
    @Test
    void shouldFailIfNoMatchedRulesGiven() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(multiRule()))
          .hasMessage("No matched rules given. Please provide at least one matched rule index.");

      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(multiRule()))
          .hasMessage("No matched rules given. Please provide at least one matched rule index.");
    }

    @Test
    void shouldFailIfIllegalIndexValueGiven() {
      // then
      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(multiRule(), -1))
          .hasMessage("Matched rule indexes contain illegal values: [-1]");

      Assertions.assertThatThrownBy(() -> asserter.hasNotMatchedRules(multiRule(), -1))
          .hasMessage("Matched rule indexes contain illegal values: [-1]");

      Assertions.assertThatThrownBy(() -> asserter.hasMatchedRules(multiRule(), -1, 0))
          .hasMessage("Matched rule indexes contain illegal values: [-1, 0]");

      Assertions.assertThatThrownBy(() -> asserter.hasNotMatchedRules(multiRule(), -1, 0))
          .hasMessage("Matched rule indexes contain illegal values: [-1, 0]");
    }
  }
}
