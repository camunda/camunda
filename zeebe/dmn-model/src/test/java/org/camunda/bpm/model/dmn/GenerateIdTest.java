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
import org.camunda.bpm.model.dmn.instance.Output;
import org.junit.Test;

public class GenerateIdTest {

  @Test
  public void shouldNotGenerateIdsOnRead() {
    DmnModelInstance modelInstance = Dmn.readModelFromStream(GenerateIdTest.class.getResourceAsStream("GenerateIdTest.dmn"));
    Definitions definitions = modelInstance.getDefinitions();
    assertThat(definitions.getId()).isNull();

    Decision decision = modelInstance.getModelElementsByType(Decision.class).iterator().next();
    assertThat(decision.getId()).isNull();

    DecisionTable decisionTable = modelInstance.getModelElementsByType(DecisionTable.class).iterator().next();
    assertThat(decisionTable.getId()).isNull();

    Output output = modelInstance.getModelElementsByType(Output.class).iterator().next();
    assertThat(output.getId()).isNull();
  }

  @Test
  public void shouldGenerateIdsOnCreate() {
    DmnModelInstance modelInstance = Dmn.createEmptyModel();
    Definitions definitions = modelInstance.newInstance(Definitions.class);
    assertThat(definitions.getId()).isNotNull();

    Decision decision = modelInstance.newInstance(Decision.class);
    assertThat(decision.getId()).isNotNull();

    DecisionTable decisionTable = modelInstance.newInstance(DecisionTable.class);
    assertThat(decisionTable.getId()).isNotNull();

    Output output = modelInstance.newInstance(Output.class);
    assertThat(output.getId()).isNotNull();
  }

}
