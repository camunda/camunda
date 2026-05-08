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
package io.camunda.process.test.impl.coverage.core;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.process.test.api.coverage.CoverageDataSource;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionCoverage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for creating and aggregating decision coverage data.
 *
 * <p>This class provides functionality to generate coverage metrics for DMN decision tables by
 * analyzing decision instances and their matched rules. It can create individual coverage reports
 * for decision instances and aggregate multiple coverage reports into consolidated results.
 */
public class DecisionCoverageCreator {

  /**
   * Creates a coverage report for a single decision instance.
   *
   * <p>Retrieves the matched rules for the given decision instance and calculates the coverage
   * percentage based on the total rule count in the decision model.
   *
   * @param dataSource The data source to retrieve decision instance details
   * @param decisionInstance The decision instance to analyze (from search results)
   * @param model The decision model containing rule count information
   * @return A DecisionCoverage object containing the coverage details
   */
  public static DecisionCoverage createCoverage(
      final CoverageDataSource dataSource,
      final DecisionInstance decisionInstance,
      final DecisionModel model) {
    final DecisionInstance detailedInstance =
        dataSource
            .getDecisionInstancesByDecisionInstanceId()
            .get(decisionInstance.getDecisionInstanceId());

    final List<String> matchedRuleIds =
        detailedInstance.getMatchedRules() == null
            ? new ArrayList<>()
            : detailedInstance.getMatchedRules().stream()
                .map(rule -> rule.getRuleId())
                .distinct()
                .collect(Collectors.toList());

    final List<Integer> matchedRuleIndices =
        detailedInstance.getMatchedRules() == null
            ? new ArrayList<>()
            : detailedInstance.getMatchedRules().stream()
                .map(rule -> rule.getRuleIndex())
                .distinct()
                .collect(Collectors.toList());

    return ImmutableDecisionCoverage.builder()
        .decisionDefinitionId(decisionInstance.getDecisionDefinitionId())
        .addAllMatchedRuleIds(matchedRuleIds)
        .addAllMatchedRuleIndices(matchedRuleIndices)
        .coverage(calculateCoverage(matchedRuleIds, model))
        .build();
  }

  /**
   * Aggregates multiple decision coverage reports into consolidated reports per decision
   * definition.
   *
   * <p>Combines coverage data from multiple evaluations of the same decision, ensuring matched
   * rules are counted only once in the aggregated result.
   *
   * @param coverages Collection of individual coverage reports to aggregate
   * @param models Collection of decision models for coverage calculation
   * @return List of aggregated DecisionCoverage objects, one per decision definition
   */
  public static List<DecisionCoverage> aggregateCoverages(
      final Collection<DecisionCoverage> coverages, final Collection<DecisionModel> models) {
    final Map<String, List<DecisionCoverage>> coveragesByDecisionDefinition =
        coverages.stream()
            .collect(Collectors.groupingBy(DecisionCoverage::getDecisionDefinitionId));

    final List<DecisionCoverage> aggregatedCoverages = new ArrayList<>();
    coveragesByDecisionDefinition.forEach(
        (decisionDefinitionId, coveragesForDecision) -> {
          final List<String> matchedRuleIds =
              coveragesForDecision.stream()
                  .flatMap(c -> c.getMatchedRuleIds().stream())
                  .distinct()
                  .collect(Collectors.toList());
          final List<Integer> matchedRuleIndices =
              coveragesForDecision.stream()
                  .flatMap(c -> c.getMatchedRuleIndices().stream())
                  .distinct()
                  .collect(Collectors.toList());
          final DecisionModel model =
              models.stream()
                  .filter(m -> m.getDecisionDefinitionId().equals(decisionDefinitionId))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "No model found for decision definition id: "
                                  + decisionDefinitionId));
          aggregatedCoverages.add(
              ImmutableDecisionCoverage.builder()
                  .decisionDefinitionId(decisionDefinitionId)
                  .addAllMatchedRuleIds(matchedRuleIds)
                  .addAllMatchedRuleIndices(matchedRuleIndices)
                  .coverage(calculateCoverage(matchedRuleIds, model))
                  .build());
        });
    return aggregatedCoverages;
  }

  /**
   * Calculates the coverage percentage for a decision instance.
   *
   * @param matchedRuleIds List of rule IDs that were matched
   * @param model The decision model containing rule count information
   * @return Coverage percentage as a value between 0.0 and 1.0
   */
  private static double calculateCoverage(
      final List<String> matchedRuleIds, final DecisionModel model) {
    if (model.getTotalRuleCount() == 0) {
      return 0.0;
    }
    return (double) matchedRuleIds.size() / model.getTotalRuleCount();
  }
}
