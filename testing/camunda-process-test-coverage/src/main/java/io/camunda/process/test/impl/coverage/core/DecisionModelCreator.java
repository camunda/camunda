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
    final CoverageDecisionDefinitionData decisionDefinitionData =
        testResults.getDecisionDefinitionData().stream()
            .filter(
                data ->
                    data.getDecisionDefinition().getDmnDecisionId().equals(decisionDefinitionId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No decision definition data found for ID: " + decisionDefinitionId));

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
   * Counts the number of rules in the decision table for the specified decision.
   *
   * <p>Navigates from the decision element (by ID) to its decision table child and counts the rule
   * elements directly.
   *
   * @param modelInstance The parsed DMN model instance
   * @param decisionDefinitionId The ID of the decision to count rules for
   * @return The number of rules in the decision table, or 0 if the decision has no table
   */
  static int countRulesForDecision(
      final DmnModelInstance modelInstance, final String decisionDefinitionId) {
    final Decision decision = modelInstance.getModelElementById(decisionDefinitionId);
    if (decision == null) {
      return 0;
    }
    return decision.getChildElementsByType(DecisionTable.class).stream()
        .mapToInt(dt -> dt.getChildElementsByType(Rule.class).size())
        .sum();
  }
}
