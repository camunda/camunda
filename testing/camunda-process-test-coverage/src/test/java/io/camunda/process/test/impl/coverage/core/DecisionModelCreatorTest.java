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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;

class DecisionModelCreatorTest {

  private static final String DECISION_ID = "my-decision";
  private static final String DECISION_NAME = "My Decision";

  /**
   * Builds a DMN XML string for a decision table with the given ID, name, and number of rules. All
   * rules have a single string input and a single string output.
   */
  static String buildDmnXml(
      final String decisionId, final String decisionName, final int ruleCount) {
    final StringBuilder rules = new StringBuilder();
    for (int i = 1; i <= ruleCount; i++) {
      rules.append(
          String.format(
              "<rule id=\"rule%d\">"
                  + "<inputEntry id=\"inputEntry%d\"><text>\"val%d\"</text></inputEntry>"
                  + "<outputEntry id=\"outputEntry%d\"><text>\"result%d\"</text></outputEntry>"
                  + "</rule>",
              i, i, i, i, i));
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\""
        + " id=\"Definitions_1\" name=\"DRD\" namespace=\"http://camunda.org/schema/1.0/dmn\">"
        + "<decision id=\""
        + decisionId
        + "\" name=\""
        + decisionName
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

  @Test
  void shouldCreateModelWithDecisionIdNameVersionAndXml() {
    // given
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);
    when(decisionDefinition.getDmnDecisionName()).thenReturn(DECISION_NAME);
    when(decisionDefinition.getVersion()).thenReturn(1);

    final String dmnXml = buildDmnXml(DECISION_ID, DECISION_NAME, 2);

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml(dmnXml)
                    .build())
            .build();

    // when
    final DecisionModel model = DecisionModelCreator.createModel(testData, DECISION_ID);

    // then
    assertThat(model.getDecisionDefinitionId()).isEqualTo(DECISION_ID);
    assertThat(model.getDecisionName()).isEqualTo(DECISION_NAME);
    assertThat(model.getVersion()).isEqualTo("1");
    assertThat(model.getXml()).contains(DECISION_ID);
  }

  @Test
  void shouldCountRulesInDecisionTable() {
    // given: a decision table with 3 rules
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);
    when(decisionDefinition.getDmnDecisionName()).thenReturn(DECISION_NAME);
    when(decisionDefinition.getVersion()).thenReturn(1);

    final String dmnXml = buildDmnXml(DECISION_ID, DECISION_NAME, 3);

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml(dmnXml)
                    .build())
            .build();

    // when
    final DecisionModel model = DecisionModelCreator.createModel(testData, DECISION_ID);

    // then
    assertThat(model.getTotalRuleCount()).isEqualTo(3);
  }

  @Test
  void shouldReturnZeroRulesWhenDecisionHasNoTable() {
    // given: a DMN model with a decision that has no decision table
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn("no-table-decision");
    when(decisionDefinition.getDmnDecisionName()).thenReturn("No Table");
    when(decisionDefinition.getVersion()).thenReturn(1);

    final String dmnXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\""
            + " id=\"Definitions_1\" name=\"DRD\" namespace=\"http://camunda.org/schema/1.0/dmn\">"
            + "<decision id=\"no-table-decision\" name=\"No Table\">"
            + "</decision>"
            + "</definitions>";

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml(dmnXml)
                    .build())
            .build();

    // when
    final DecisionModel model = DecisionModelCreator.createModel(testData, "no-table-decision");

    // then
    assertThat(model.getTotalRuleCount()).isEqualTo(0);
  }

  @Test
  void shouldReturnNullDecisionNameWhenNotDefined() {
    // given
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);
    when(decisionDefinition.getDmnDecisionName()).thenReturn(null);
    when(decisionDefinition.getVersion()).thenReturn(3);

    final String dmnXml = buildDmnXml(DECISION_ID, DECISION_ID, 1);

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml(dmnXml)
                    .build())
            .build();

    // when
    final DecisionModel model = DecisionModelCreator.createModel(testData, DECISION_ID);

    // then
    assertThat(model.getDecisionName()).isNull();
    assertThat(model.getVersion()).isEqualTo("3");
  }

  @Test
  void shouldThrowWhenDecisionDefinitionNotFound() {
    // given
    final ImmutableCoverageTestData testData = ImmutableCoverageTestData.builder().build();

    // then
    assertThatThrownBy(() -> DecisionModelCreator.createModel(testData, "unknown-decision"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No decision definition data found for ID: unknown-decision");
  }

  @Test
  void shouldThrowWhenDmnXmlIsEmpty() {
    // given
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addDecisionDefinitionData(
                ImmutableCoverageDecisionDefinitionData.builder()
                    .decisionDefinition(decisionDefinition)
                    .xml("")
                    .build())
            .build();

    // then
    assertThatThrownBy(() -> DecisionModelCreator.createModel(testData, DECISION_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot read DMN XML from decision definition");
  }

  @Test
  void shouldReturnZeroRulesForUnknownDecisionIdInModelInstance() {
    // given: a DMN model instance that does not contain the requested decision id
    final DmnModelInstance modelInstance =
        Dmn.readModelFromStream(
            new java.io.ByteArrayInputStream(buildDmnXml("other-decision", "Other", 2).getBytes()));

    // when
    final int count =
        DecisionModelCreator.countRulesForDecision(modelInstance, "non-existent-decision");

    // then
    assertThat(count).isEqualTo(0);
  }

  @Test
  void shouldCountRulesForDecisionDirectly() {
    // given
    final DmnModelInstance modelInstance =
        Dmn.readModelFromStream(
            new java.io.ByteArrayInputStream(
                buildDmnXml(DECISION_ID, DECISION_NAME, 4).getBytes()));

    // when
    final int count = DecisionModelCreator.countRulesForDecision(modelInstance, DECISION_ID);

    // then
    assertThat(count).isEqualTo(4);
  }
}
