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
package io.camunda.zeebe.model.bpmn.validation;

import static io.camunda.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ZeebeCaseValidationTest {

  @Test
  void shouldAcceptCaseMarkerOnProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("valid-case").zeebeCase("loanTitle").startEvent().done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldAcceptProcessWithoutCaseMarker() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("no-case-marker").startEvent().done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldRejectMultipleCaseMarkers() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("duplicate-case")
            .zeebeCase("firstTitle")
            .zeebeCase("secondTitle")
            .startEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(Process.class, "Must have exactly one 'zeebe:case' extension element when exists."));
  }

  @Test
  void shouldRejectCaseMarkerOnNonProcessElement() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("case-on-task")
            .startEvent()
            .serviceTask(
                "case-task",
                t ->
                    t.zeebeJobType("type")
                        .addExtensionElement(ZeebeCase.class, c -> c.setTitle("loanTitle")))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeCase.class,
            "The case marker 'zeebe:case' is only allowed on a process, but was found on 'serviceTask'"));
  }

  @Test
  void shouldRejectCaseMarkerInsideDocumentation() {
    // given
    final String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""
            + " targetNamespace=\"http://bpmn.io/schema/bpmn\">"
            + "<bpmn:process id=\"case-in-documentation\" isExecutable=\"true\">"
            + "<bpmn:documentation><zeebe:case title=\"loanTitle\" /></bpmn:documentation>"
            + "<bpmn:startEvent id=\"start\" />"
            + "</bpmn:process>"
            + "</bpmn:definitions>";
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeCase.class,
            "The case marker 'zeebe:case' is only allowed on a process, but was found on 'documentation'"));
  }

  @Test
  void shouldRejectCaseMarkerWithoutTitle() {
    // given
    final BpmnModelInstance process = processWithCaseXml("missing-title", "<zeebe:case />");

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeCase.class, "Attribute 'title' must be present and not empty"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   "})
  void shouldRejectCaseMarkerWithBlankTitle(final String title) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("blank-title").zeebeCase(title).startEvent().done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeCase.class, "Attribute 'title' must be present and not empty"));
  }

  @Test
  void shouldRejectCaseMarkerWithExpressionTitle() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("expression-title").zeebeCase("=foo").startEvent().done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeCase.class, "Attribute 'title' must be a variable name, not an expression"));
  }

  private static BpmnModelInstance processWithCaseXml(
      final String processId, final String caseElementXml) {
    final String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""
            + " targetNamespace=\"http://bpmn.io/schema/bpmn\">"
            + "<bpmn:process id=\""
            + processId
            + "\" isExecutable=\"true\">"
            + "<bpmn:extensionElements>"
            + caseElementXml
            + "</bpmn:extensionElements>"
            + "<bpmn:startEvent id=\"start\" />"
            + "</bpmn:process>"
            + "</bpmn:definitions>";
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
