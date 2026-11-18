/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.InputValues;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.OutputEntry;
import org.camunda.bpm.model.dmn.instance.OutputValues;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.dmn.instance.Text;
import org.camunda.bpm.model.dmn.util.DmnModelResource;
import org.junit.Test;

public class ExpressionLanguageTest extends DmnModelTest {

  public static final String EXPRESSION_LANGUAGE_DMN = "org/camunda/bpm/model/dmn/ExpressionLanguageTest.dmn";
  public static final String EXPRESSION_LANGUAGE = "juel";


  @Test
  @DmnModelResource(resource = EXPRESSION_LANGUAGE_DMN)
  public void shouldReadExpressionLanguage() {
    Definitions definitions = modelInstance.getDefinitions();
    assertThat(definitions.getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);

    DecisionTable decisionTable = modelInstance.getModelElementById("decisionTable");
    Input input = decisionTable.getInputs().iterator().next();
    assertThat(input.getInputExpression().getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);
    assertThat(input.getInputValues().getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);
    Output output = decisionTable.getOutputs().iterator().next();
    assertThat(output.getOutputValues().getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);

    Rule rule = decisionTable.getRules().iterator().next();
    InputEntry inputEntry = rule.getInputEntries().iterator().next();
    assertThat(inputEntry.getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);
    OutputEntry outputEntry = rule.getOutputEntries().iterator().next();
    assertThat(outputEntry.getExpressionLanguage()).isEqualTo(EXPRESSION_LANGUAGE);
  }

  @Test
  public void shouldWriteExpressionLanguage() throws Exception {
    modelInstance = Dmn.createEmptyModel();
    Definitions definitions = generateNamedElement(Definitions.class, "definitions");
    definitions.setNamespace(TEST_NAMESPACE);
    definitions.setExpressionLanguage(EXPRESSION_LANGUAGE);
    modelInstance.setDocumentElement(definitions);

    Decision decision = generateNamedElement(Decision.class, "Check Order");
    definitions.addChildElement(decision);

    DecisionTable decisionTable = generateElement(DecisionTable.class);
    decision.addChildElement(decisionTable);

    Input input = generateElement(Input.class);
    decisionTable.getInputs().add(input);
    InputExpression inputExpression = generateElement(InputExpression.class);
    inputExpression.setExpressionLanguage(EXPRESSION_LANGUAGE);
    input.setInputExpression(inputExpression);
    InputValues inputValues = generateElement(InputValues.class);
    inputValues.setExpressionLanguage(EXPRESSION_LANGUAGE);
    inputValues.setText(generateElement(Text.class));
    input.setInputValues(inputValues);

    Output output = generateElement(Output.class);
    decisionTable.getOutputs().add(output);
    OutputValues outputValues = generateElement(OutputValues.class);
    outputValues.setExpressionLanguage(EXPRESSION_LANGUAGE);
    outputValues.setText(generateElement(Text.class));
    output.setOutputValues(outputValues);

    Rule rule = generateElement(Rule.class);
    decisionTable.getRules().add(rule);
    InputEntry inputEntry = generateElement(InputEntry.class);
    inputEntry.setExpressionLanguage(EXPRESSION_LANGUAGE);
    inputEntry.setText(generateElement(Text.class));
    rule.getInputEntries().add(inputEntry);
    OutputEntry outputEntry = generateElement(OutputEntry.class);
    outputEntry.setExpressionLanguage(EXPRESSION_LANGUAGE);
    rule.getOutputEntries().add(outputEntry);

    assertModelEqualsFile(EXPRESSION_LANGUAGE_DMN);
  }

}
