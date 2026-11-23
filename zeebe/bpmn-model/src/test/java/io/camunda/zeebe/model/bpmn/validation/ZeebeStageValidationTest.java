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
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeStage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ZeebeStageValidationTest {

  private static final String CASE_TITLE = "caseTitle";
  private static final String ONLY_ON_SUB_PROCESS_MESSAGE =
      "The stage marker 'zeebe:stage' is only allowed on a sub-process, but was found on '%s'";
  private static final String ONLY_TOP_LEVEL_MESSAGE =
      "The stage marker 'zeebe:stage' is only allowed on a top-level sub-process, but was found on a sub-process nested in '%s'";
  private static final String ONLY_IN_CASE_MESSAGE =
      "The stage marker 'zeebe:stage' is only allowed in a process that is marked as a case with 'zeebe:case'";

  @Test
  void shouldAcceptStageOnTopLevelSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-on-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess("stage", stage -> stage.zeebeStage().embeddedSubProcess().startEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldAcceptStageOnTopLevelAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-on-ad-hoc-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .adHocSubProcess(
                "stage",
                stage -> {
                  stage.zeebeStage();
                  stage.task("task");
                })
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldAcceptStageOnTopLevelEventSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-on-event-sub-process")
            .zeebeCase(CASE_TITLE)
            .eventSubProcess(
                "stage", stage -> stage.zeebeStage().startEvent().signal("signal").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldAcceptCaseWithoutStage() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("case-without-stage")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess("sub-process", subProcess -> subProcess.embeddedSubProcess().startEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldRejectStageOnSubProcessNestedInSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-nested-in-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess("outer")
            .embeddedSubProcess()
            .startEvent()
            .subProcess("stage", stage -> stage.zeebeStage().embeddedSubProcess().startEvent())
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeStage.class, String.format(ONLY_TOP_LEVEL_MESSAGE, "subProcess")));
  }

  @Test
  void shouldRejectStageOnAdHocSubProcessNestedInSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-ad-hoc-nested-in-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess("outer")
            .embeddedSubProcess()
            .startEvent()
            .adHocSubProcess(
                "stage",
                stage -> {
                  stage.zeebeStage();
                  stage.task("task");
                })
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeStage.class, String.format(ONLY_TOP_LEVEL_MESSAGE, "subProcess")));
  }

  @Test
  void shouldRejectStageOnEventSubProcessNestedInSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-event-nested-in-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess("outer")
            .embeddedSubProcess()
            .eventSubProcess(
                "stage", stage -> stage.zeebeStage().startEvent().signal("signal").endEvent())
            .startEvent()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeStage.class, String.format(ONLY_TOP_LEVEL_MESSAGE, "subProcess")));
  }

  @Test
  void shouldRejectStageOnSubProcessNestedInAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-nested-in-ad-hoc-sub-process")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .adHocSubProcess(
                "outer",
                outer ->
                    outer.subProcess(
                        "stage", stage -> stage.zeebeStage().embeddedSubProcess().startEvent()))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeStage.class, String.format(ONLY_TOP_LEVEL_MESSAGE, "adHocSubProcess")));
  }

  @Test
  void shouldRejectStageInProcessWithoutCaseMarker() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-without-case")
            .startEvent()
            .subProcess("stage", stage -> stage.zeebeStage().embeddedSubProcess().startEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeStage.class, ONLY_IN_CASE_MESSAGE));
  }

  @Test
  void shouldRejectMultipleStageMarkersOnSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("duplicate-stage")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .subProcess(
                "stage", stage -> stage.zeebeStage().zeebeStage().embeddedSubProcess().startEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SubProcess.class,
            "Must have exactly one 'zeebe:stage' extension element when exists."));
  }

  @Test
  void shouldRejectStageMarkerOnNonSubProcessElement() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-on-task")
            .zeebeCase(CASE_TITLE)
            .startEvent()
            .serviceTask(
                "stage-task",
                task ->
                    task.zeebeJobType("type").addExtensionElement(ZeebeStage.class, stage -> {}))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeStage.class, String.format(ONLY_ON_SUB_PROCESS_MESSAGE, "serviceTask")));
  }

  @Test
  void shouldRejectStageMarkerOnProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("stage-on-process")
            .zeebeCase(CASE_TITLE)
            .addExtensionElement(ZeebeStage.class, stage -> {})
            .startEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeStage.class, String.format(ONLY_ON_SUB_PROCESS_MESSAGE, "process")));
  }

  @Test
  void shouldRejectStageMarkerInsideDocumentation() {
    // given
    final String xml =
        String.join(
            "\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                  xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\"",
            "                  targetNamespace=\"http://bpmn.io/schema/bpmn\">",
            "  <bpmn:process id=\"stage-in-documentation\" isExecutable=\"true\">",
            "    <bpmn:documentation><zeebe:stage /></bpmn:documentation>",
            "    <bpmn:startEvent id=\"start\" />",
            "  </bpmn:process>",
            "</bpmn:definitions>");
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeStage.class, String.format(ONLY_ON_SUB_PROCESS_MESSAGE, "documentation")));
  }

  @Test
  void shouldReportDuplicateCaseMarkersWithoutFailingOnStage() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("duplicate-case-with-stage")
            .zeebeCase("firstTitle")
            .zeebeCase("secondTitle")
            .startEvent()
            .subProcess("stage", stage -> stage.zeebeStage().embeddedSubProcess().startEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(Process.class, "Must have exactly one 'zeebe:case' extension element when exists."));
  }
}
