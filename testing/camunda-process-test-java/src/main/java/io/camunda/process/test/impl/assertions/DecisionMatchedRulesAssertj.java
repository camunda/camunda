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

  public void hasMatchedRules(
      final List<Integer> actualMatchedRuleIndexes, final int... expectedMatchedRuleIndexes) {

    final List<Integer> expectedMatches =
        Arrays.stream(expectedMatchedRuleIndexes).boxed().collect(Collectors.toList());

    Assertions.assertThat(actualMatchedRuleIndexes)
        .withFailMessage(
            "%s to have matched rules %s, but did not. Matches:\n"
                + "\t- matched: %s\n"
                + "\t- missing: %s\n"
                + "\t- unexpected: %s",
            actual,
            Arrays.toString(expectedMatchedRuleIndexes),
            matchingRules(actualMatchedRuleIndexes, expectedMatches),
            missingRules(actualMatchedRuleIndexes, expectedMatches),
            unexpectedRules(actualMatchedRuleIndexes, expectedMatches))
        .containsAll(expectedMatches);
  }

  public void hasNotMatchedRules(
      final List<Integer> actualMatchedRuleIndexes, final int... expectedUnmatchedRuleIndexes) {
    final List<Integer> expectedUnmatchedRules =
        Arrays.stream(expectedUnmatchedRuleIndexes).boxed().collect(Collectors.toList());

    Assertions.assertThat(actualMatchedRuleIndexes)
        .withFailMessage(
            "%s to not have matched rules %s, but matched %s",
            actual,
            Arrays.toString(expectedUnmatchedRuleIndexes),
            matchingRules(actualMatchedRuleIndexes, expectedUnmatchedRules))
        .doesNotContainAnyElementsOf(expectedUnmatchedRules);
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
}
