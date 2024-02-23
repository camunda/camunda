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
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZeebeSignalValidationTest {

  @Test
  @DisplayName("A signal start event must have a name")
  void emptySignalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().signal("").done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Signal.class, "Name must be present and not empty"));
  }

  @Test
  @DisplayName("A signal start event could have a static name")
  void signalStartEventName() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().signal("signalName").done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A signal start event could have a expression name")
  void signalStartEventNameExpression() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(s -> s.nameExpression("signal_val"))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A signal start event could have a custom id")
  void signalStartEventWithCustomId() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(s -> s.id("signalId").name("signalName"))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Multiple signal start event with different signal names are allowed")
  void multipleSignalStartEventName() {
    // given
    final BpmnModelInstance process = getProcessWithMultipleSignalStartEvents();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Multiple signal start event with the same signal name are not allowed")
  void multipleSignalStartEventWithSameSignalName() {
    // given
    final BpmnModelInstance process = getProcessWithMultipleStartEventsWithSameSignal();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Multiple signal event definitions with the same name 'signalName' are not allowed."));
  }

  @Test
  @DisplayName("A intermediate signal catch event must have a name")
  void emptyIntermediateCatchSignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .signal("")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Signal.class, "Name must be present and not empty"));
  }

  @Test
  @DisplayName("A intermediate signal catch event could have a static name")
  void intermediateCatchSignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .signal("signalName")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A intermediate signal throw event must have a name")
  void emptyIntermediateThrowSignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent("foo")
            .signal("")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Signal.class, "Name must be present and not empty"));
  }

  @Test
  @DisplayName("A intermediate signal throw event could have a static name")
  void intermediateThrowSignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent("foo")
            .signal("signalName")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A boundary signal event must have a name")
  void emptyBoundarySignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name(null)))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name(null)))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(Signal.class, "Name must be present and not empty"),
        expect(Signal.class, "Name must be present and not empty"));
  }

  @Test
  @DisplayName(
      "A task with multiple signal boundary event definitions with the same name are not allowed")
  void sameBoundarySignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name("signalName")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name("signalName")))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ServiceTask.class,
            "Multiple signal event definitions with the same name 'signalName' are not allowed."));
  }

  @Test
  @DisplayName(
      "A task with multiple signal boundary event definitions with different names are allowed")
  void differentBoundarySignalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name("signalName1")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name("signalName2")))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Must reference a signal")
  void checkReferenceSignal() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, e -> e.setType("type"))
            .signalEventDefinition()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(SignalEventDefinition.class, "Must reference a signal"));
  }

  @Test
  @DisplayName("Different event definitions with the same signal name are allowed")
  void differentEventWithSameSignalName() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(m -> m.id("start-signal").name("signalName"))
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent(
                "boundary-1", b -> b.signal(s -> s.id("boundary-signal").name("signalName")))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Signal event are allowed in an event sub process")
  void eventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent() {
    // given
    final BpmnModelInstance process =
        getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A signal end event must hava a name")
  void emptySignalEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().signal("").done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Signal.class, "Name must be present and not empty"));
  }

  @Test
  @DisplayName("A end event could support signal")
  void signalEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().signal("signalName").done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  private static BpmnModelInstance getProcessWithMultipleStartEventsWithSameSignal() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    final String signalName = "signalName";
    process.startEvent("start1").signal(s -> s.id("start-signal").name(signalName)).endEvent();
    process.startEvent("start2").signal(signalName).endEvent();
    return process.done();
  }

  private static BpmnModelInstance getProcessWithMultipleSignalStartEvents() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    process.startEvent().signal("s1").endEvent();
    process.startEvent().signal("s2").endEvent();
    return process.startEvent().signal("s3").endEvent().done();
  }

  private static BpmnModelInstance
      getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent(
            "event_sub_start",
            a -> a.signal(s -> s.id("event_sub_start_signal").name("signalName")))
        .subProcess(
            "embedded",
            sub ->
                sub.boundaryEvent(
                    "boundary-msg", s -> s.signal("signalName").endEvent("boundary-end")))
        .embeddedSubProcess()
        .startEvent("embedded_sub_start")
        .endEvent("embedded_sub_end")
        .moveToNode("embedded")
        .endEvent("event_sub_end");
    return builder.startEvent("start").endEvent("end").done();
  }
}
