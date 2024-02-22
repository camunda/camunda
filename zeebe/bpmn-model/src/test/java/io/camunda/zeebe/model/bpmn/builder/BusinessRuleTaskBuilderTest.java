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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

public class BusinessRuleTaskBuilderTest {

  @Test
  void shouldSetDecisionId() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task", task -> task.zeebeCalledDecisionId("decision-id-1"))
            .done();

    // then
    final ModelElementInstance businessRuleTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) businessRuleTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledDecision.class))
        .hasSize(1)
        .extracting(ZeebeCalledDecision::getDecisionId)
        .containsExactly("decision-id-1");
  }

  @Test
  void shouldSetDecisionIdExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask(
                "task", task -> task.zeebeCalledDecisionIdExpression("decisionIdExpr"))
            .done();

    // then
    final ModelElementInstance businessRuleTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) businessRuleTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledDecision.class))
        .hasSize(1)
        .extracting(ZeebeCalledDecision::getDecisionId)
        .containsExactly("=decisionIdExpr");
  }

  @Test
  void shouldSetResultVariable() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task", task -> task.zeebeResultVariable("result"))
            .done();

    // then
    final ModelElementInstance businessRuleTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) businessRuleTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledDecision.class))
        .hasSize(1)
        .extracting(ZeebeCalledDecision::getResultVariable)
        .containsExactly("result");
  }

  @Test
  void shouldSetDecisionIdAndResultVariable() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask(
                "task",
                task -> task.zeebeCalledDecisionId("decision-id-1").zeebeResultVariable("result"))
            .done();

    // then
    final ModelElementInstance businessRuleTask = instance.getModelElementById("task");
    final ExtensionElements extensionElements =
        (ExtensionElements) businessRuleTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledDecision.class))
        .hasSize(1)
        .extracting(ZeebeCalledDecision::getDecisionId, ZeebeCalledDecision::getResultVariable)
        .containsExactly(tuple("decision-id-1", "result"));
  }
}
