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
package io.camunda.process.test.impl.coverage.report;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.impl.coverage.core.DecisionModelCreator;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds decision models for report tests the way the coverage collector builds them at runtime, so
 * that a model's rule count and its DMN cannot drift apart.
 */
final class DecisionModelFixtures {

  private DecisionModelFixtures() {}

  static DecisionModel modelOf(final String decisionDefinitionId, final String... ruleIds) {
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(decisionDefinitionId);
    when(decisionDefinition.getVersion()).thenReturn(1);

    return DecisionModelCreator.createModel(
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml(dmnOf(decisionDefinitionId, ruleIds))
                    .build())
            .build(),
        decisionDefinitionId);
  }

  private static String dmnOf(final String decisionDefinitionId, final String... ruleIds) {
    final String rules =
        Arrays.stream(ruleIds)
            .map(
                ruleId ->
                    String.format(
                        "<rule id=\"%s\">"
                            + "<inputEntry id=\"input-%s\"><text>\"in\"</text></inputEntry>"
                            + "<outputEntry id=\"output-%s\"><text>\"out\"</text></outputEntry>"
                            + "</rule>",
                        ruleId, ruleId, ruleId))
            .collect(Collectors.joining());

    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\""
        + " id=\"Definitions_1\" name=\"DRD\" namespace=\"http://camunda.org/schema/1.0/dmn\">"
        + "<decision id=\""
        + decisionDefinitionId
        + "\" name=\""
        + decisionDefinitionId
        + "\">"
        + "<decisionTable id=\"decisionTable_1\">"
        + "<input id=\"input_1\">"
        + "<inputExpression id=\"inputExpr_1\" typeRef=\"string\"><text>input</text></inputExpression>"
        + "</input>"
        + "<output id=\"output_1\" name=\"result\" typeRef=\"string\"/>"
        + rules
        + "</decisionTable>"
        + "</decision>"
        + "</definitions>";
  }
}
