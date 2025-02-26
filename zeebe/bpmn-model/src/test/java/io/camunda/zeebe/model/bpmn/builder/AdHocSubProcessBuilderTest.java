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
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.CompletionCondition;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class AdHocSubProcessBuilderTest {

  @Test
  void shouldAddElementsToAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                subprocess -> {
                  subprocess.task("A");
                  subprocess.task("B");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);

    assertThat(adHocSubProcess.getChildElementsByType(FlowElement.class))
        .hasSize(2)
        .extracting(FlowElement::getId)
        .contains("A", "B");
  }

  @Test
  void shouldSetActiveElementsCollection() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"A\"]");
                  adHocSubProcess.task("A");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    final ExtensionElements extensionElements =
        (ExtensionElements) adHocSubProcess.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements).isNotNull();

    assertThat(extensionElements.getChildElementsByType(ZeebeAdHoc.class))
        .hasSize(1)
        .extracting(ZeebeAdHoc::getActiveElementsCollection)
        .contains("=[\"A\"]");
  }

  @Test
  void shouldSetCompletionCondition() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.completionCondition("true");
                  adHocSubProcess.task("A");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");
    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);

    assertThat(((AdHocSubProcess) adHocSubProcess).getCompletionCondition())
        .isNotNull()
        .isInstanceOf(CompletionCondition.class)
        .extracting(CompletionCondition::getTextContent)
        .isEqualTo("=true");
  }

  @Test
  void shouldSetCancelRemainingInstances() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.cancelRemainingInstancesEnabled(true);
                  adHocSubProcess.task("A");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);
    assertThat(((AdHocSubProcess) adHocSubProcess).isCancelRemainingInstancesEnabled()).isTrue();
  }
}
