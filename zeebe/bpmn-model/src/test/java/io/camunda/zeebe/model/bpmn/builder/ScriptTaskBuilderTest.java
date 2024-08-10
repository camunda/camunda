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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

public class ScriptTaskBuilderTest {

  @Test
  void shouldSetExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .scriptTask("task", task -> task.zeebeExpression("true"))
            .done();

    // then
    final ModelElementInstance scriptTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) scriptTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeScript.class))
        .hasSize(1)
        .extracting(ZeebeScript::getExpression)
        .containsExactly("=true");
  }

  @Test
  void shouldSetResultVariable() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .scriptTask("task", task -> task.zeebeResultVariable("result"))
            .done();

    // then
    final ModelElementInstance scriptTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) scriptTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeScript.class))
        .hasSize(1)
        .extracting(ZeebeScript::getResultVariable)
        .containsExactly("result");
  }

  @Test
  void shouldSetExpressionAndResultVariable() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .scriptTask(
                "task", task -> task.zeebeExpression("expression").zeebeResultVariable("result"))
            .done();

    // then
    final ModelElementInstance scriptTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) scriptTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeScript.class))
        .hasSize(1)
        .extracting(ZeebeScript::getExpression, ZeebeScript::getResultVariable)
        .containsExactly(tuple("=expression", "result"));
  }
}
