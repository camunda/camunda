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
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.CompletionCondition;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetCancelRemainingInstances(final boolean cancelRemainingInstances) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.cancelRemainingInstances(cancelRemainingInstances);
                  adHocSubProcess.task("A");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);
    assertThat(((AdHocSubProcess) adHocSubProcess).isCancelRemainingInstances())
        .isEqualTo(cancelRemainingInstances);
  }

  @Test
  void cancelRemainingInstancesShouldDefaultToTrue() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.task("A");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);
    assertThat(((AdHocSubProcess) adHocSubProcess).isCancelRemainingInstances()).isTrue();
  }

  @Test
  void shouldSetTaskDefinition() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess ->
                    adHocSubProcess.zeebeJobType("jobType").zeebeJobRetries("3").task("A"))
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    final ExtensionElements extensionElements =
        (ExtensionElements) adHocSubProcess.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements).isNotNull();

    assertThat(extensionElements.getChildElementsByType(ZeebeTaskDefinition.class))
        .hasSize(1)
        .first()
        .extracting(ZeebeTaskDefinition::getType, ZeebeTaskDefinition::getRetries)
        .containsExactly("jobType", "3");
  }

  @Test
  void shouldSetTaskDefinitionAsExpressions() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess ->
                    adHocSubProcess
                        .zeebeJobTypeExpression("jobType")
                        .zeebeJobRetriesExpression("3")
                        .task("A"))
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    final ExtensionElements extensionElements =
        (ExtensionElements) adHocSubProcess.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements).isNotNull();

    assertThat(extensionElements.getChildElementsByType(ZeebeTaskDefinition.class))
        .hasSize(1)
        .first()
        .extracting(ZeebeTaskDefinition::getType, ZeebeTaskDefinition::getRetries)
        .containsExactly("=jobType", "=3");
  }

  @Test
  void shouldSetTaskHeaders() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess ->
                    adHocSubProcess
                        .zeebeJobType("jobType")
                        .zeebeTaskHeader("headerKey1", "headerValue1")
                        .zeebeTaskHeader("headerKey2", "headerValue2")
                        .task("A"))
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    final ExtensionElements extensionElements =
        (ExtensionElements) adHocSubProcess.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements).isNotNull();

    assertThat(extensionElements.getChildElementsByType(ZeebeTaskHeaders.class))
        .hasSize(1)
        .first()
        .extracting(ZeebeTaskHeaders::getHeaders)
        .satisfies(
            headers ->
                assertThat(headers)
                    .extracting(ZeebeHeader::getKey, ZeebeHeader::getValue)
                    .containsExactly(
                        tuple("headerKey1", "headerValue1"), tuple("headerKey2", "headerValue2")));
  }

  @Test
  void shouldSetOutputCollectionAndElement() {
    // given
    final String outputElementExpression = "result";
    final String outputCollection = "results";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
            .zeebeOutputCollection(outputCollection)
            .zeebeOutputElementExpression(outputElementExpression)
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    final ExtensionElements extensionElements =
        (ExtensionElements) adHocSubProcess.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements).isNotNull();

    assertThat(extensionElements.getChildElementsByType(ZeebeAdHoc.class))
        .hasSize(1)
        .first()
        .extracting(ZeebeAdHoc::getOutputElement, ZeebeAdHoc::getOutputCollection)
        .containsExactly("=" + outputElementExpression, outputCollection);
  }
}
