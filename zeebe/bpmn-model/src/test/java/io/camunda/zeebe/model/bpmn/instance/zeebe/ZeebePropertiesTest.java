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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Test;

public class ZeebePropertiesTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BpmnModelConstants.ZEEBE_NS, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(BpmnModelConstants.ZEEBE_NS, ZeebeProperty.class));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Collections.emptyList();
  }

  @Test
  public void shouldReadExtensionElements() {
    // given
    modelInstance =
        Bpmn.readModelFromStream(
            ZeebePropertiesTest.class.getResourceAsStream("ZeebePropertiesTest.bpmn"));

    final ModelElementInstance start = modelInstance.getModelElementById("start");
    final ModelElementInstance fork = modelInstance.getModelElementById("fork");
    final ModelElementInstance task = modelInstance.getModelElementById("task");

    // when
    final Map<String, String> startProperties = getZeebePropertiesAsMap(start);
    final Map<String, String> forkProperties = getZeebePropertiesAsMap(fork);
    final Map<String, String> taskProperties = getZeebePropertiesAsMap(task);

    // then
    assertThat(startProperties).containsOnly(entry("id", "start"), entry("type", "event"));
    assertThat(forkProperties).containsOnly(entry("id", "fork"), entry("type", "gateway"));
    assertThat(taskProperties).containsOnly(entry("id", "task"), entry("type", "task"));
  }

  private Map<String, String> getZeebePropertiesAsMap(final ModelElementInstance elementInstance) {
    return elementInstance
        .getUniqueChildElementByType(ExtensionElements.class)
        .getUniqueChildElementByType(ZeebeProperties.class)
        .getChildElementsByType(ZeebeProperty.class)
        .stream()
        .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue));
  }
}
