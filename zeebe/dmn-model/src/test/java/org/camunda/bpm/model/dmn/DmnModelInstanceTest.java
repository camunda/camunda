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

import org.camunda.bpm.model.dmn.impl.DmnModelConstants;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.junit.Test;

/**
 * @author Filip Hrisafov
 */
public class DmnModelInstanceTest {

  @Test
  public void testClone() throws Exception {

    DmnModelInstance modelInstance = Dmn.createEmptyModel();

    Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setId("TestId");
    modelInstance.setDefinitions(definitions);

    DmnModelInstance cloneInstance = modelInstance.clone();
    cloneInstance.getDefinitions().setId("TestId2");

    assertThat(modelInstance.getDefinitions().getId()).isEqualTo("TestId");
    assertThat(cloneInstance.getDefinitions().getId()).isEqualTo("TestId2");
  }

  @Test
  public void shouldExportDmnDiagramWithLatestDmnNamespace() {
    // given
    DmnModelInstance modelInstance = Dmn.createEmptyModel();
    Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
    definitions.setName("definitions");
    definitions.setId("definitions");
    modelInstance.setDefinitions(definitions);

    // when
    String exportedDiagram = Dmn.convertToString(modelInstance);

    // then
    assertThat(exportedDiagram).contains(DmnModelConstants.LATEST_DMN_NS);
  }

}
