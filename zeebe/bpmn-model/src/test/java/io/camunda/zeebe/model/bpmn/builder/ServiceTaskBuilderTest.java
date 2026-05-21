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

import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.beforeAll;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.end;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.start;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
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

  @Test
  void shouldDefineExecutionListenerTaskHeadersForServiceTask() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .zeebeExecutionListener(
                            listener ->
                                listener
                                    .start()
                                    .type("el_start_type")
                                    .zeebeTaskHeader("aKey", "aValue")
                                    .zeebeTaskHeader("bKey", "bValue")))
            .done();

    // then
    assertThat(getExecutionListeners(instance.getModelElementById("task")))
        .singleElement()
        .satisfies(
            listener ->
                assertThat(listener.getTaskHeaders().getHeaders())
                    .extracting(ZeebeHeader::getKey, ZeebeHeader::getValue)
                    .containsExactly(tuple("aKey", "aValue"), tuple("bKey", "bValue")));
  }

  @Test
  void shouldDefineBeforeAllExecutionListenerWithCustomRetries() {
    // given
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .multiInstance(b -> b.zeebeInputCollectionExpression("items"))
                        .zeebeBeforeAllExecutionListener("el_before_all_type", "5"))
            .done();

    // then
    assertThat(getExecutionListeners(instance.getModelElementById("task")))
        .singleElement()
        .extracting(
            ZeebeExecutionListener::getEventType,
            ZeebeExecutionListener::getType,
            ZeebeExecutionListener::getRetries)
        .containsExactly(beforeAll, "el_before_all_type", "5");
  }

  @Test
  void shouldDefineBeforeAllStartAndEndExecutionListenersTogether() {
    // given
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .multiInstance(b -> b.zeebeInputCollectionExpression("items"))
                        .zeebeBeforeAllExecutionListener("el_before_all")
                        .zeebeStartExecutionListener("el_start")
                        .zeebeEndExecutionListener("el_end"))
            .done();

    // then
    assertThat(getExecutionListeners(instance.getModelElementById("task")))
        .hasSize(3)
        .extracting(
            ZeebeExecutionListener::getEventType,
            ZeebeExecutionListener::getType,
            ZeebeExecutionListener::getRetries)
        .containsExactly(
            tuple(beforeAll, "el_before_all", ZeebeExecutionListener.DEFAULT_RETRIES),
            tuple(start, "el_start", ZeebeExecutionListener.DEFAULT_RETRIES),
            tuple(end, "el_end", ZeebeExecutionListener.DEFAULT_RETRIES));
  }

  private Collection<ZeebeExecutionListener> getExecutionListeners(
      final ModelElementInstance elementInstance) {
    return elementInstance
        .getUniqueChildElementByType(ExtensionElements.class)
        .getUniqueChildElementByType(ZeebeExecutionListeners.class)
        .getChildElementsByType(ZeebeExecutionListener.class);
  }

  @Test
  void shouldSetJobPriorityAsLiteralOnServiceTask() {
    // given / when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriority("42"))
            .endEvent()
            .done();

    // then
    final ModelElementInstance serviceTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) serviceTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeJobPriorityDefinition.class))
        .singleElement()
        .extracting(ZeebeJobPriorityDefinition::getPriority)
        .isEqualTo("42");
  }

  @Test
  void shouldSetJobPriorityAsExpressionOnServiceTask() {
    // given / when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriorityExpression("priority"))
            .endEvent()
            .done();

    // then
    final ModelElementInstance serviceTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) serviceTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeJobPriorityDefinition.class))
        .singleElement()
        .extracting(ZeebeJobPriorityDefinition::getPriority)
        .isEqualTo("=priority");
  }
}
