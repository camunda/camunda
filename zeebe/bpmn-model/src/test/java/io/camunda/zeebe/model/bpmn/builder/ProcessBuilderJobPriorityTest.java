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

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class ProcessBuilderJobPriorityTest {

  @Test
  void shouldSetJobPriorityOnProcess() {
    // given
    final String priority = "10";

    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriority(priority)
            .startEvent()
            .endEvent()
            .done();

    // then
    final ModelElementInstance process = instance.getModelElementById("process");
    final ExtensionElements extensionElements =
        (ExtensionElements) process.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeJobPriorityDefinition.class))
        .hasSize(1)
        .extracting(ZeebeJobPriorityDefinition::getPriority)
        .containsExactly(priority);
  }

  @Test
  void shouldSetJobPriorityExpressionOnProcess() {
    // given
    final String expression = "customer.tier";

    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriorityExpression(expression)
            .startEvent()
            .endEvent()
            .done();

    // then
    final ModelElementInstance process = instance.getModelElementById("process");
    final ExtensionElements extensionElements =
        (ExtensionElements) process.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeJobPriorityDefinition.class))
        .hasSize(1)
        .extracting(ZeebeJobPriorityDefinition::getPriority)
        .containsExactly("=" + expression);
  }
}
