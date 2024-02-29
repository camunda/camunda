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
package io.camunda.zeebe.model.bpmn.builder;

import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.end;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

public class ServiceTaskBuilderTest {

  @Test
  void shouldSetServiceTaskPropertiesAsExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobTypeExpression("expressionType")
                        .zeebeJobRetriesExpression("expressionRetries"))
            .done();

    // then
    final ModelElementInstance serviceTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) serviceTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeTaskDefinition.class))
        .hasSize(1)
        .extracting(ZeebeTaskDefinition::getType, ZeebeTaskDefinition::getRetries)
        .containsExactly(tuple("=expressionType", "=expressionRetries"));
  }

  @Test
  void shouldDefineExecutionListenersForServiceTask() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .zeebeJobRetries("6")
                        .zeebeStartExecutionListener("el_start_type_1")
                        .zeebeStartExecutionListener("el_start_type_2", "2")
                        .zeebeEndExecutionListener("el_end_type_1", "5")
                        .zeebeEndExecutionListener("el_end_type_2"))
            .done();

    // then
    final ModelElementInstance serviceTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) serviceTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeTaskDefinition.class))
        .hasSize(1)
        .extracting(ZeebeTaskDefinition::getType, ZeebeTaskDefinition::getRetries)
        .containsExactly(tuple("service_task_type", "6"));
    assertThat(getExecutionListeners(serviceTask))
        .hasSize(4)
        .extracting(
            ZeebeExecutionListener::getEventType,
            ZeebeExecutionListener::getType,
            ZeebeExecutionListener::getRetries)
        .containsExactly(
            tuple(start, "el_start_type_1", ZeebeExecutionListener.DEFAULT_RETRIES),
            tuple(start, "el_start_type_2", "2"),
            tuple(end, "el_end_type_1", "5"),
            tuple(end, "el_end_type_2", ZeebeExecutionListener.DEFAULT_RETRIES));
  }

  private Collection<ZeebeExecutionListener> getExecutionListeners(
      final ModelElementInstance elementInstance) {
    return elementInstance
        .getUniqueChildElementByType(ExtensionElements.class)
        .getUniqueChildElementByType(ZeebeExecutionListeners.class)
        .getChildElementsByType(ZeebeExecutionListener.class);
  }
}
