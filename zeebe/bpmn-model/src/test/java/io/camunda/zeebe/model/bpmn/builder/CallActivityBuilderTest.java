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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class CallActivityBuilderTest {

  @Test
  void shouldSetProcessId() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("callActivity", c -> c.zeebeProcessId("process-id-1"))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::getProcessId)
        .containsExactly("process-id-1");
  }

  @Test
  void shouldSetProcessIdExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("callActivity", c -> c.zeebeProcessIdExpression("processIdExpr"))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::getProcessId)
        .containsExactly("=processIdExpr");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetPropagateAllChildVariables(final boolean propagateAllChildVariables) {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity(
                "callActivity", c -> c.zeebePropagateAllChildVariables(propagateAllChildVariables))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::isPropagateAllChildVariablesEnabled)
        .containsExactly(propagateAllChildVariables);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetPropagateAllParentVariables(final boolean propagateAllParentVariables) {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity(
                "callActivity",
                c -> c.zeebePropagateAllParentVariables(propagateAllParentVariables))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::isPropagateAllParentVariablesEnabled)
        .containsExactly(propagateAllParentVariables);
  }

  @ParameterizedTest
  @EnumSource(ZeebeBindingType.class)
  void shouldSetBindingType(final ZeebeBindingType bindingType) {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("callActivity", c -> c.zeebeBindingType(bindingType))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::getBindingType)
        .containsExactly(bindingType);
  }

  @Test
  void shouldSetVersionTag() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("callActivity", c -> c.zeebeVersionTag("v1"))
            .done();

    // then
    final ModelElementInstance callActivity = instance.getModelElementById("callActivity");
    final ExtensionElements extensionElements =
        (ExtensionElements) callActivity.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeCalledElement.class))
        .hasSize(1)
        .extracting(ZeebeCalledElement::getVersionTag)
        .containsExactly("v1");
  }
}
