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

import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionCoverage;
import io.camunda.process.test.impl.coverage.data.CoverageDecisionInstanceData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
   * @param decisionInstanceResult The decision instance to analyze (from search results)
   * @param model The decision model containing rule count information
   * @return A DecisionCoverage object containing the coverage details
   */
  public static DecisionCoverage createCoverage(
      final CoverageDecisionInstanceData decisionInstanceResult, final DecisionModel model) {
    final DecisionInstance decisionInstance = decisionInstanceResult.getDecisionInstance();

    final Map<String, Integer> matchedRules = new LinkedHashMap<>();
    for (final MatchedDecisionRule matchedRule : matchedRules(decisionInstance)) {
      matchedRules.putIfAbsent(matchedRule.getRuleId(), matchedRule.getRuleIndex());
    }
    final Map<String, Integer> coveredRules = retainCoverable(matchedRules, model);

    return ImmutableDecisionCoverage.builder()
        .decisionDefinitionId(decisionInstance.getDecisionDefinitionId())
        .addAllMatchedRuleIds(coveredRules.keySet())
        .addAllMatchedRuleIndices(
            coveredRules.values().stream().filter(Objects::nonNull).collect(Collectors.toList()))
        .coverage(calculateCoverage(coveredRules.keySet(), model))
        .build();
  }

  private static List<MatchedDecisionRule> matchedRules(final DecisionInstance decisionInstance) {
    return decisionInstance.getMatchedRules() == null
        ? Collections.emptyList()
        : decisionInstance.getMatchedRules();
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
        (decisionDefinitionId, coveragesForDecision) ->
            aggregatedCoverages.add(aggregate(decisionDefinitionId, coveragesForDecision, models)));
    return aggregatedCoverages;
  }

  /**
   * Measures a coverage against the table the report describes its decision by.
   *
   * <p>A coverage is collected against the deployment that was evaluated, which is not necessarily
   * the deployment the report describes the decision by: suites can deploy tables that differ in
   * their rules under one decision definition id. The report renders the coverage against the table
   * it selected, so rules of another deployment neither count towards the percentage nor are
   * highlighted in that table.
   *
   * @param coverage The coverage as it was collected
   * @param models The tables the report describes the decisions by
   * @return The coverage, measured against the table of its decision definition id
   */
  public static DecisionCoverage measureAgainstReportedModel(
      final DecisionCoverage coverage, final Collection<DecisionModel> models) {
    return aggregate(
        coverage.getDecisionDefinitionId(), Collections.singletonList(coverage), models);
  }

  private static DecisionCoverage aggregate(
      final String decisionDefinitionId,
      final List<DecisionCoverage> coverages,
      final Collection<DecisionModel> models) {
    final DecisionModel model =
        models.stream()
            .filter(m -> m.getDecisionDefinitionId().equals(decisionDefinitionId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No model found for decision definition id: " + decisionDefinitionId));

    final Map<String, Integer> matchedRules = new LinkedHashMap<>();
    coverages.forEach(
        coverage -> {
          final List<String> ruleIds = coverage.getMatchedRuleIds();
          final List<Integer> ruleIndices = coverage.getMatchedRuleIndices();
          for (int i = 0; i < ruleIds.size(); i++) {
            matchedRules.putIfAbsent(
                ruleIds.get(i), i < ruleIndices.size() ? ruleIndices.get(i) : null);
          }
        });
    final Map<String, Integer> coveredRules = retainCoverable(matchedRules, model);

    return ImmutableDecisionCoverage.builder()
        .decisionDefinitionId(decisionDefinitionId)
        .addAllMatchedRuleIds(coveredRules.keySet())
        .addAllMatchedRuleIndices(
            coveredRules.values().stream().filter(Objects::nonNull).collect(Collectors.toList()))
        .coverage(calculateCoverage(coveredRules.keySet(), model))
        .build();
  }

  /**
   * Retains the rules that the reported table can cover.
   *
   * <p>A decision definition id can be covered by more than one table, for example when suites
   * deploy fixtures that differ in their rules. The report describes such a decision by a single
   * table, so rules of the other tables are not part of its coverage: they are neither counted nor
   * highlighted.
   *
   * <p>Nothing is retained away when the rules of the reported table cannot be identified, because
   * the rules of the other tables cannot be told apart from them then.
   *
   * @param matchedRules The matched rules by their id, in the order they were matched
   * @param model The model the report describes the decision by
   * @return The matched rules that are rules of the reported table
   */
  private static Map<String, Integer> retainCoverable(
      final Map<String, Integer> matchedRules, final DecisionModel model) {
    final Set<String> coverableRuleIds = DecisionModelCreator.coverableRuleIds(model);
    if (coverableRuleIds.isEmpty()) {
      return matchedRules;
    }

    final Map<String, Integer> coveredRules = new LinkedHashMap<>();
    matchedRules.forEach(
        (ruleId, ruleIndex) -> {
          if (coverableRuleIds.contains(ruleId)) {
            coveredRules.put(ruleId, ruleIndex);
          }
        });
    return coveredRules;
  }

  /**
   * Calculates the coverage percentage for a decision instance.
   *
   * @param matchedRuleIds List of rule IDs that were matched
   * @param model The decision model containing rule count information
   * @return ProcessCoverage percentage as a value between 0.0 and 1.0
   */
  private static double calculateCoverage(
      final Collection<String> matchedRuleIds, final DecisionModel model) {
    if (model.getTotalRuleCount() == 0) {
      return 0.0;
    }
    return (double) matchedRuleIds.size() / model.getTotalRuleCount();
  }
}
