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

import static org.assertj.core.api.Fail.fail;

import io.camunda.client.api.response.MatchedDecisionRule;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class DecisionMatchedRulesAssertj
    extends AbstractAssert<DecisionMatchedRulesAssertj, String> {

  public DecisionMatchedRulesAssertj(final String failureMessagePrefix) {

    super(failureMessagePrefix, DecisionMatchedRulesAssertj.class);
  }

  public void hasNoMatchedRules(final List<MatchedDecisionRule> matchedRules) {
    final List<Integer> actualMatchedRuleIndices =
        matchedRules.stream().map(MatchedDecisionRule::getRuleIndex).collect(Collectors.toList());

    Assertions.assertThat(matchedRules)
        .withFailMessage("%s to have no matches, but matched %s", actual, actualMatchedRuleIndices)
        .isEmpty();
  }

  public void hasMatchedRules(
      final List<MatchedDecisionRule> matchedRules, final int... expectedMatchedRuleIndexes) {

    final List<Integer> expectedMatches = validateExpectedRuleIndexes(expectedMatchedRuleIndexes);
    final List<Integer> actualMatchedRuleIndices =
        matchedRules.stream().map(MatchedDecisionRule::getRuleIndex).collect(Collectors.toList());

    Assertions.assertThat(actualMatchedRuleIndices)
        .withFailMessage(
            "%s to have matched rules %s, but did not. Matches:\n"
                + "\t- matched: %s\n"
                + "\t- missing: %s\n"
                + "\t- unexpected: %s",
            actual,
            Arrays.toString(expectedMatchedRuleIndexes),
            matchingRules(actualMatchedRuleIndices, expectedMatches),
            missingRules(actualMatchedRuleIndices, expectedMatches),
            unexpectedRules(actualMatchedRuleIndices, expectedMatches))
        .containsAll(expectedMatches);
  }

  public void hasNotMatchedRules(
      final List<MatchedDecisionRule> matchedRules, final int... expectedUnmatchedRuleIndexes) {

    final List<Integer> expectedUnmatchedRuleIndexList =
        validateExpectedRuleIndexes(expectedUnmatchedRuleIndexes);
    final List<Integer> actualMatchedRuleIndexes =
        matchedRules.stream().map(MatchedDecisionRule::getRuleIndex).collect(Collectors.toList());

    Assertions.assertThat(actualMatchedRuleIndexes)
        .withFailMessage(
            "%s to not have matched rules %s, but matched %s",
            actual,
            Arrays.toString(expectedUnmatchedRuleIndexes),
            matchingRules(actualMatchedRuleIndexes, expectedUnmatchedRuleIndexList))
        .doesNotContainAnyElementsOf(expectedUnmatchedRuleIndexList);
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

  private List<Integer> validateExpectedRuleIndexes(final int... ruleIndexes) {
    if (ruleIndexes.length == 0) {
      fail("No matched rules given. Please provide at least one matched rule index.");
    }

    final List<Integer> ruleIndexList =
        Arrays.stream(ruleIndexes).boxed().collect(Collectors.toList());

    if (ruleIndexList.stream().anyMatch(i -> i <= 0)) {
      fail("Matched rule indexes contain illegal values: %s", Arrays.toString(ruleIndexes));
    }

    return ruleIndexList;
  }
}
