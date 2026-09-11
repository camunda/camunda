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

import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionModel;
import io.camunda.process.test.impl.coverage.data.CoverageDecisionDefinitionData;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Rule;

/**
 * Utility class for creating decision models from Camunda engine definitions.
 *
 * <p>This class provides functionality to retrieve DMN models from the Camunda engine, parse their
 * structure, and create DecisionModel objects that contain information about the decision table
 * rules for coverage analysis.
 */
public class DecisionModelCreator {

  /**
   * The rules of a table, kept for as long as the table is measured against.
   *
   * <p>A suite reports itself after every test, and reporting measures every coverage of every run
   * it collected so far against the table it describes the decision by. Reading the rules of that
   * table from its XML each time would parse it once per coverage, so the number of parses would
   * grow with the square of the tests in the suite. A table never changes, so its rules are read
   * once and shared from here. There is an entry per table the suite deployed, which the suite
   * holds on to anyway.
   */
  private static final Map<DecisionModel, Map<String, Integer>> COVERABLE_RULE_INDICES_BY_MODEL =
      new ConcurrentHashMap<>();

  /**
   * Creates a decision model object from a decision definition in the Camunda engine.
   *
   * <p>Retrieves the DMN XML for the specified decision definition, parses it to find the decision
   * table for the given decision, and counts the rules for coverage analysis.
   *
   * @param testResults The data source to retrieve decision definition data
   * @param decisionDefinitionId The ID of the decision definition to create a model for
   * @return A DecisionModel object containing decision structure information and rule counts
   * @throws IllegalArgumentException if the model cannot be read from the decision definition
   */
  public static DecisionModel createModel(
      final CoverageTestData testResults, final String decisionDefinitionId) {
    return createModel(testResults, decisionDefinitionId, null);
  }

  /**
   * Creates a decision model object from a decision definition in the Camunda engine.
   *
   * <p>A decision definition id can be deployed several times within a test run, for example when
   * suites deploy fixtures that differ in their rules. The deployment that was evaluated describes
   * the decision; the other deployments describe a different decision under the same id, so no
   * other deployment stands in for it.
   *
   * @param testResults The data source to retrieve decision definition data
   * @param decisionDefinitionId The ID of the decision definition to create a model for
   * @param decisionDefinitionKey The key of the deployment that was evaluated, or {@code null} if
   *     unknown
   * @return A DecisionModel object containing decision structure information and rule counts
   * @throws IllegalArgumentException if the model cannot be read from the decision definition
   */
  public static DecisionModel createModel(
      final CoverageTestData testResults,
      final String decisionDefinitionId,
      final Long decisionDefinitionKey) {
    final CoverageDecisionDefinitionData decisionDefinitionData =
        selectDeployment(testResults, decisionDefinitionId, decisionDefinitionKey)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No decision definition data found for ID: "
                            + decisionDefinitionId
                            + (decisionDefinitionKey == null
                                ? ""
                                : " deployed under key: " + decisionDefinitionKey)));

    final String xml = decisionDefinitionData.getXml();

    if (xml == null || xml.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot read DMN XML from decision definition: " + decisionDefinitionId);
    }

    final DmnModelInstance modelInstance =
        Dmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));

    final int ruleCount = countRulesForDecision(modelInstance, decisionDefinitionId);

    return ImmutableDecisionModel.builder()
        .decisionDefinitionId(decisionDefinitionId)
        .decisionName(decisionDefinitionData.getDecisionDefinition().getDmnDecisionName())
        .totalRuleCount(ruleCount)
        .version(String.valueOf(decisionDefinitionData.getDecisionDefinition().getVersion()))
        .xml(xml)
        .build();
  }

  /**
   * Selects the deployment that was evaluated, out of the deployments of a decision definition id.
   *
   * <p>Another deployment of the id does not stand in for it: it describes a different decision, so
   * its table would neither explain what was evaluated nor let its rules count as coverage.
   *
   * @param testResults The data source to retrieve decision definition data
   * @param decisionDefinitionId The ID of the decision definition
   * @param decisionDefinitionKey The key of the deployment that was evaluated, or {@code null} if
   *     unknown
   * @return The deployment that was evaluated, or any deployment of the id when the key is unknown
   */
  private static Optional<CoverageDecisionDefinitionData> selectDeployment(
      final CoverageTestData testResults,
      final String decisionDefinitionId,
      final Long decisionDefinitionKey) {

    final Stream<CoverageDecisionDefinitionData> deploymentsOfId =
        testResults.getDecisionDefinitionData().stream()
            .filter(
                data ->
                    data.getDecisionDefinition().getDmnDecisionId().equals(decisionDefinitionId));

    if (decisionDefinitionKey == null) {
      return deploymentsOfId.findFirst();
    }

    return deploymentsOfId
        .filter(data -> decisionDefinitionKey.equals(data.getDecisionDefinition().getDecisionKey()))
        .findFirst();
  }

  /**
   * Numbers the rules a model can cover the way the engine numbers them: by their position in the
   * table, starting at one. The rules counted by {@link #createModel} are the rules numbered here.
   *
   * <p>A rule id can sit at a different position in another deployment of the decision definition
   * id, so a coverage measured against this model must take its indices from here rather than from
   * the table that was evaluated.
   *
   * <p>Yields no rule when the rules of the model cannot be identified, for example because its DMN
   * cannot be read or its rules carry no id. A caller cannot tell the rules of this table from the
   * rules of another table then, and must not mistake the empty result for a table without rules.
   *
   * @param model The model to number the rules of
   * @return The position of each rule of the decision table by its id, in table order
   */
  public static Map<String, Integer> coverableRuleIndicesById(final DecisionModel model) {
    return COVERABLE_RULE_INDICES_BY_MODEL.computeIfAbsent(
        model, table -> Collections.unmodifiableMap(readCoverableRuleIndicesById(table)));
  }

  private static Map<String, Integer> readCoverableRuleIndicesById(final DecisionModel model) {
    final List<Rule> rules;
    try {
      rules =
          rulesOf(
              Dmn.readModelFromStream(new ByteArrayInputStream(model.getXml().getBytes())),
              model.getDecisionDefinitionId());
    } catch (final RuntimeException e) {
      return Collections.emptyMap();
    }

    final Map<String, Integer> ruleIndicesById = new LinkedHashMap<>();
    for (int position = 0; position < rules.size(); position++) {
      final String ruleId = rules.get(position).getId();
      if (ruleId != null && !ruleId.isEmpty()) {
        ruleIndicesById.putIfAbsent(ruleId, position + 1);
      }
    }
    return ruleIndicesById;
  }

  /**
   * Selects the table that describes more of the decision out of two models sharing a decision
   * definition id.
   *
   * <p>A decision definition id can be deployed with different tables within the same test run, for
   * example when suites use fixtures that differ in their rules. The report describes such a
   * decision by its richest table, so that the rules of the other tables cannot count as coverage
   * of a table that does not have them.
   *
   * @param model A model of the decision
   * @param otherModel Another model of the same decision
   * @return The model with the higher rule count, or the first one if both are equal
   */
  public static DecisionModel selectMostCompleteModel(
      final DecisionModel model, final DecisionModel otherModel) {
    return otherModel.getTotalRuleCount() > model.getTotalRuleCount() ? otherModel : model;
  }

  /**
   * Counts the number of rules in the decision table for the specified decision.
   *
   * @param modelInstance The parsed DMN model instance
   * @param decisionDefinitionId The ID of the decision to count rules for
   * @return The number of rules in the decision table, or 0 if the decision has no table
   */
  static int countRulesForDecision(
      final DmnModelInstance modelInstance, final String decisionDefinitionId) {
    return rulesOf(modelInstance, decisionDefinitionId).size();
  }

  /**
   * Collects the rules of a decision, in the order its tables list them.
   *
   * @param modelInstance The parsed DMN model instance
   * @param decisionDefinitionId The ID of the decision to collect the rules of
   * @return The rules of the decision, or none if the model does not define it
   */
  private static List<Rule> rulesOf(
      final DmnModelInstance modelInstance, final String decisionDefinitionId) {
    final Decision decision = modelInstance.getModelElementById(decisionDefinitionId);
    if (decision == null) {
      return Collections.emptyList();
    }
    return decision.getChildElementsByType(DecisionTable.class).stream()
        .flatMap(decisionTable -> decisionTable.getChildElementsByType(Rule.class).stream())
        .collect(Collectors.toList());
  }
}
