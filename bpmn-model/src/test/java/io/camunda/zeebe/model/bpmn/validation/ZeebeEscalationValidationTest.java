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
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.Escalation;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZeebeEscalationValidationTest {

  private BpmnModelInstance process(final Consumer<SubProcessBuilder> builder) {
    return Bpmn.createExecutableProcess().startEvent().subProcess("sp", builder).endEvent().done();
  }

  @Test
  @DisplayName(
      "An escalation boundary event should only allow attaching to sub-process or call-activity")
  void verifyEscalationBoundaryEventOnlyAllowAttachingToSubProcessOrCallActivity() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.escalation("escalation-1"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            BoundaryEvent.class,
            "An escalation boundary event should only be attached to a subprocess, or a call activity"));
  }

  @Test
  @DisplayName("An escalation boundary event should allow attaching to sub-process")
  void verifyEscalationBoundaryEventAttachingToSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess(
                "sp",
                s -> {
                  s.embeddedSubProcess()
                      .startEvent()
                      .endEvent("end", e -> e.escalation("escalation"));
                })
            .boundaryEvent("catch", b -> b.escalation("escalation"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("An escalation boundary event should allow attaching to call-activity")
  void verifyEscalationBoundaryEventAttachingToCallActivity() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation().endEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName(
      "Multiple escalation boundary event definitions without escalation code are not allowed")
  void verifyMultipleEscalationBoundaryEventsWithoutEscalationCode() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess()
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation().endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation().endEvent())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CallActivity.class,
            "The same scope can not contain more than one escalation catch event without escalation code. An escalation catch event without escalation code catches all escalations."));
  }

  @Test
  @DisplayName("Multiple escalation boundary events with the same escalationCode are not allowed")
  void verifyMultipleEscalationBoundaryEventsWithSameEscalationCode() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation("escalation").endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation("escalation").endEvent())
            .moveToNode("call")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CallActivity.class,
            "Multiple escalation catch events with the same escalation code 'escalation' are not supported on the same scope."));
  }

  @Test
  @DisplayName(
      "The same scope can not contains an escalation boundary event without escalation code and another one with escalation code.")
  void verifyMultipleEscalationBoundaryEventsWithAndWithOutEscalationCode() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch-1", b -> b.escalation().endEvent())
            .moveToActivity("call")
            .boundaryEvent("catch-2", b -> b.escalation("escalation").endEvent())
            .moveToNode("call")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CallActivity.class,
            "The same scope can not contain an escalation catch event without escalation code and another one with escalation code. An escalation catch event without escalation code catches all escalations."));
  }

  @Test
  @DisplayName("A sub-process with multiple escalation start event definitions are not allowed")
  void verifyMultipleEscalationEventSubprocessWithoutEscalationCode() {
    // given
    final BpmnModelInstance process =
        process(
            sp -> {
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("")
                  .endEvent();
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("")
                  .endEvent();
              sp.embeddedSubProcess().startEvent().endEvent();
            });

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SubProcess.class,
            "The same scope can not contain more than one escalation catch event without escalation code. An escalation catch event without escalation code catches all escalations."));
  }

  @Test
  @DisplayName("A sub-process with multiple escalation start event definitions are not allowed")
  void verifyMultipleEscalationEventSubprocessWithAndWithoutEscalationCode() {
    // given
    final BpmnModelInstance process =
        process(
            sp -> {
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation()
                  .endEvent();
              sp.embeddedSubProcess()
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .escalation("escalation")
                  .endEvent();
              sp.embeddedSubProcess().startEvent().endEvent();
            });

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SubProcess.class,
            "The same scope can not contain an escalation catch event without escalation code and another one with escalation code. An escalation catch event without escalation code catches all escalations."));
  }

  @Test
  @DisplayName("Multiple event subprocess with the same escalationCode are not allowed")
  void verifyMultipleEscalationEventSubprocessWithSameEscalationCode() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub-1", s -> s.startEvent().interrupting(true).escalation("escalation").endEvent())
            .eventSubProcess(
                "sub-2", s -> s.startEvent().interrupting(true).escalation("escalation").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Multiple escalation catch events with the same escalation code 'escalation' are not supported on the same scope."));
  }

  @Test
  @DisplayName("An escalation throw event definition cannot omit escalationRef")
  void verifyMissingEscalationRefOnIntermediateThrowingEscalationEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .escalationEventDefinition()
            .escalationEventDefinitionDone()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(EscalationEventDefinition.class, "Must reference an escalation"));
  }

  @Test
  @DisplayName("An escalation throw event definition cannot omit escalationCode")
  void verifyMissingEscalationCodeOnIntermediateThrowingEscalationEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .escalation("")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Escalation.class, "EscalationCode must be present and not empty"));
  }

  @Test
  @DisplayName("An escalation end event definition cannot omit escalationRef")
  void verifyMissingEscalationRefOnEscalationEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("escalation", AbstractThrowEventBuilder::escalationEventDefinition)
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(EscalationEventDefinition.class, "Must reference an escalation"));
  }

  @Test
  @DisplayName(
      "An escalation end event definition cannot omit escalationCode of referenced escalation")
  void verifyMissingEscalationCodeOnEscalationEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("escalation", e -> e.escalation(""))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Escalation.class, "EscalationCode must be present and not empty"));
  }

  @Test
  @DisplayName("An escalation boundary event can not contain an expression")
  void verifyEscalationCodeOfEscalationBoundaryEventCanNotContainAnExpression() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .boundaryEvent("catch", b -> b.escalation("= escalation").endEvent())
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            EscalationEventDefinition.class,
            "The escalationCode of the escalation catch event is not allowed to be an expression"));
  }

  @Test
  @DisplayName("An escalation start event can not contain an expression")
  void verifyEscalationCodeOfEscalationStartEventCanNotContainAnExpression() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub", s -> s.startEvent("start").escalation("= escalation").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            EscalationEventDefinition.class,
            "The escalationCode of the escalation catch event is not allowed to be an expression"));
  }
}
