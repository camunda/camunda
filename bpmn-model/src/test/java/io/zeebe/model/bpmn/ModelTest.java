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

package io.zeebe.model.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.Definitions;
import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.StartEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.Test;

public class ModelTest {

  @Test
  public void testCreateEmptyModel() {
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();

    Definitions definitions = bpmnModelInstance.getDefinitions();
    assertThat(definitions).isNull();

    definitions = bpmnModelInstance.newInstance(Definitions.class);
    bpmnModelInstance.setDefinitions(definitions);

    definitions = bpmnModelInstance.getDefinitions();
    assertThat(definitions).isNotNull();
  }

  @Test
  public void testBaseTypeCalculation() {
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();
    final Model model = bpmnModelInstance.getModel();
    Collection<ModelElementType> allBaseTypes =
        ModelUtil.calculateAllBaseTypes(model.getType(StartEvent.class));
    assertThat(allBaseTypes).hasSize(5);

    allBaseTypes = ModelUtil.calculateAllBaseTypes(model.getType(MessageEventDefinition.class));
    assertThat(allBaseTypes).hasSize(3);

    allBaseTypes = ModelUtil.calculateAllBaseTypes(model.getType(BaseElement.class));
    assertThat(allBaseTypes).hasSize(0);
  }

  @Test
  public void testExtendingTypeCalculation() {
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();
    final Model model = bpmnModelInstance.getModel();
    final List<ModelElementType> baseInstanceTypes = new ArrayList<ModelElementType>();
    baseInstanceTypes.add(model.getType(Event.class));
    baseInstanceTypes.add(model.getType(CatchEvent.class));
    baseInstanceTypes.add(model.getType(ExtensionElements.class));
    baseInstanceTypes.add(model.getType(EventDefinition.class));
    final Collection<ModelElementType> allExtendingTypes =
        ModelUtil.calculateAllExtendingTypes(bpmnModelInstance.getModel(), baseInstanceTypes);
    assertThat(allExtendingTypes).hasSize(17);
  }
}
