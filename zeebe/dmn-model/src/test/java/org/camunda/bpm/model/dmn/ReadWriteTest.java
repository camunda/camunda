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
import static org.camunda.bpm.model.dmn.DecisionTableOrientation.CrossTable;
import static org.camunda.bpm.model.dmn.DecisionTableOrientation.Rule_as_Column;
import static org.camunda.bpm.model.dmn.DecisionTableOrientation.Rule_as_Row;
import static org.camunda.bpm.model.dmn.HitPolicy.ANY;
import static org.camunda.bpm.model.dmn.HitPolicy.COLLECT;
import static org.camunda.bpm.model.dmn.HitPolicy.FIRST;
import static org.camunda.bpm.model.dmn.HitPolicy.OUTPUT_ORDER;
import static org.camunda.bpm.model.dmn.HitPolicy.PRIORITY;
import static org.camunda.bpm.model.dmn.HitPolicy.RULE_ORDER;
import static org.camunda.bpm.model.dmn.HitPolicy.UNIQUE;

import org.camunda.bpm.model.dmn.impl.instance.InputDataImpl;
import org.camunda.bpm.model.dmn.instance.*;
import org.camunda.bpm.model.dmn.util.DmnModelResource;
import org.junit.Test;

import java.util.Collection;

public class ReadWriteTest extends DmnModelTest {

  public static final String DECISION_TABLE_ORIENTATION_DMN = "org/camunda/bpm/model/dmn/ReadWriteTest.decisionTableOrientation.dmn";
  public static final String HIT_POLICY_DMN = "org/camunda/bpm/model/dmn/ReadWriteTest.hitPolicy.dmn";
  public static final String INPUT_DATA_DMN = "org/camunda/bpm/model/dmn/ReadWriteTest.inputData.dmn";

  @Test
  @DmnModelResource(resource = DECISION_TABLE_ORIENTATION_DMN)
  public void shouldReadDecisionTableOrientation() {
    // Default
    DecisionTable decisionTable = modelInstance.getModelElementById("decisionTable1");
    assertThat(decisionTable.getPreferredOrientation()).isEqualTo(Rule_as_Row);

    // Rule-as-Row
    decisionTable = modelInstance.getModelElementById("decisionTable2");
    assertThat(decisionTable.getPreferredOrientation()).isEqualTo(Rule_as_Row);

    // Rule-as-Column
    decisionTable = modelInstance.getModelElementById("decisionTable3");
    assertThat(decisionTable.getPreferredOrientation()).isEqualTo(Rule_as_Column);

    // CrossTable
    decisionTable = modelInstance.getModelElementById("decisionTable4");
    assertThat(decisionTable.getPreferredOrientation()).isEqualTo(CrossTable);
  }

  @Test
  public void shouldWriteDecisionTableOrientation() throws Exception {
    modelInstance = Dmn.createEmptyModel();
    Definitions definitions = generateNamedElement(Definitions.class, "definitions");
    definitions.setNamespace(TEST_NAMESPACE);
    modelInstance.setDocumentElement(definitions);

    // Default
    Decision decision = generateNamedElement(Decision.class, "decision1", 1);
    DecisionTable decisionTable = generateElement(DecisionTable.class, 1);
    decision.setExpression(decisionTable);
    Output output = generateElement(Output.class, 1);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // Rule-as-Row
    decision = generateNamedElement(Decision.class, "decision2", 2);
    decisionTable = generateElement(DecisionTable.class, 2);
    decisionTable.setPreferredOrientation(Rule_as_Row);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 2);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // Rule-as-Column
    decision = generateNamedElement(Decision.class, "decision3", 3);
    decisionTable = generateElement(DecisionTable.class, 3);
    decisionTable.setPreferredOrientation(Rule_as_Column);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 3);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // CrossTable
    decision = generateNamedElement(Decision.class, "decision4", 4);
    decisionTable = generateElement(DecisionTable.class, 4);
    decisionTable.setPreferredOrientation(CrossTable);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 4);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    assertModelEqualsFile(DECISION_TABLE_ORIENTATION_DMN);
  }

  @Test
  @DmnModelResource(resource = HIT_POLICY_DMN)
  public void shouldReadHitPolicy() {
    // Default
    DecisionTable decisionTable = modelInstance.getModelElementById("decisionTable1");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(UNIQUE);

    // UNIQUE
    decisionTable = modelInstance.getModelElementById("decisionTable2");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(UNIQUE);

    // FIRST
    decisionTable = modelInstance.getModelElementById("decisionTable3");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(FIRST);

    // PRIORITY
    decisionTable = modelInstance.getModelElementById("decisionTable4");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(PRIORITY);

    // ANY
    decisionTable = modelInstance.getModelElementById("decisionTable5");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(ANY);

    // COLLECT
    decisionTable = modelInstance.getModelElementById("decisionTable6");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(COLLECT);

    // RULE ORDER
    decisionTable = modelInstance.getModelElementById("decisionTable7");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(RULE_ORDER);

    // OUTPUT ORDER
    decisionTable = modelInstance.getModelElementById("decisionTable8");
    assertThat(decisionTable.getHitPolicy()).isEqualTo(OUTPUT_ORDER);

  }

  @Test
  public void shouldWriteHitPolicy() throws Exception {
    modelInstance = Dmn.createEmptyModel();
    Definitions definitions = generateNamedElement(Definitions.class, "definitions");
    definitions.setNamespace(TEST_NAMESPACE);
    modelInstance.setDocumentElement(definitions);

    // Default
    Decision decision = generateNamedElement(Decision.class, "decision1", 1);
    DecisionTable decisionTable = generateElement(DecisionTable.class, 1);
    decision.setExpression(decisionTable);
    Output output = generateElement(Output.class, 1);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // UNIQUE
    decision = generateNamedElement(Decision.class, "decision2", 2);
    decisionTable = generateElement(DecisionTable.class, 2);
    decisionTable.setHitPolicy(UNIQUE);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 2);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // FIRST
    decision = generateNamedElement(Decision.class, "decision3", 3);
    decisionTable = generateElement(DecisionTable.class, 3);
    decisionTable.setHitPolicy(FIRST);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 3);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // PRIORITY
    decision = generateNamedElement(Decision.class, "decision4", 4);
    decisionTable = generateElement(DecisionTable.class, 4);
    decisionTable.setHitPolicy(PRIORITY);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 4);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // ANY
    decision = generateNamedElement(Decision.class, "decision5", 5);
    decisionTable = generateElement(DecisionTable.class, 5);
    decisionTable.setHitPolicy(ANY);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 5);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // COLLECT
    decision = generateNamedElement(Decision.class, "decision6", 6);
    decisionTable = generateElement(DecisionTable.class, 6);
    decisionTable.setHitPolicy(COLLECT);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 6);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // RULE ORDER
    decision = generateNamedElement(Decision.class, "decision7", 7);
    decisionTable = generateElement(DecisionTable.class, 7);
    decisionTable.setHitPolicy(RULE_ORDER);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 7);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    // OUTPUT ORDER
    decision = generateNamedElement(Decision.class, "decision8", 8);
    decisionTable = generateElement(DecisionTable.class, 8);
    decisionTable.setHitPolicy(OUTPUT_ORDER);
    decision.setExpression(decisionTable);
    output = generateElement(Output.class, 8);
    decisionTable.getOutputs().add(output);
    definitions.addChildElement(decision);

    assertModelEqualsFile(HIT_POLICY_DMN);

  }

  @Test
  @DmnModelResource(resource = INPUT_DATA_DMN)
  public void shouldReadInputData() {

    Collection<InputData> inputDataCollection = modelInstance.getModelElementsByType(InputData.class);

    assertThat(inputDataCollection).hasSize(2);
    assertThat(inputDataCollection).extracting("class").contains(InputDataImpl.class);

    InputData inputData = modelInstance.getModelElementById("customerStatusIn");

    assertThat(inputData).isNotNull();
    assertThat(inputData).isInstanceOf(InputData.class);
  }
}
