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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Test;

public class ZeebeExecutionListenersTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BpmnModelConstants.ZEEBE_NS, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(BpmnModelConstants.ZEEBE_NS, ZeebeExecutionListener.class));
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
            ZeebeExecutionListenersTest.class.getResourceAsStream(
                "ZeebeExecutionListenersTest.bpmn"));

    final ModelElementInstance serviceTask = modelInstance.getModelElementById("dmk_service_task");

    // when
    final Collection<ZeebeExecutionListener> executionListeners =
        getExecutionListeners(serviceTask);

    // then
    assertThat(executionListeners)
        .extracting("eventType", "type", "retries")
        .containsExactly(
            tuple(ZeebeExecutionListenerEventType.start, "dmk_task_start_type_1", "4"),
            tuple(ZeebeExecutionListenerEventType.start, "dmk_task_start_type_2", "8"),
            tuple(ZeebeExecutionListenerEventType.end, "dmk_task_end_type_1", "5"),
            tuple(
                ZeebeExecutionListenerEventType.end,
                "dmk_task_end_type_2",
                ZeebeExecutionListener.DEFAULT_RETRIES));
  }

  private Collection<ZeebeExecutionListener> getExecutionListeners(
      final ModelElementInstance elementInstance) {
    return elementInstance
        .getUniqueChildElementByType(ExtensionElements.class)
        .getUniqueChildElementByType(ZeebeExecutionListeners.class)
        .getChildElementsByType(ZeebeExecutionListener.class);
  }
}
