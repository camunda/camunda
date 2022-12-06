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
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZeebeConditionalEventValidationTest {

  private static final String CONDITION = "= true";

  @Test
  @DisplayName("A conditional start event contain a valid expression for evaluation")
  void verifyValidConditionOfConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start", s -> s.condition(CONDITION))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A conditional start event must contain a valid expression for evaluation")
  void verifyInvalidConditionOfConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start", s -> s.condition(""))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ConditionalEventDefinition.class,
            "Conditional event must contain an expression for evaluation"));
  }

  @Test
  @DisplayName(
      "Cannot have more than one conditional event subscription with the same condition for conditional start events")
  void verifyNoDuplicatedConditionOfConditionalStartEvents() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess("process");
    processBuilder.startEvent("start-1", s -> s.condition(CONDITION)).endEvent();

    final BpmnModelInstance process =
        processBuilder.startEvent("start-2", s -> s.condition(CONDITION)).endEvent().done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Cannot have more than one conditional event subscription with the same condition '= true'"));
  }

  @Test
  @DisplayName("A boundary conditional event contain a valid expression for evaluation")
  void verifyValidConditionOfBoundaryConditionalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.condition(CONDITION))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(process);
  }

  @Test
  @DisplayName("A boundary conditional event must contain a valid expression for evaluation")
  void verifyInvalidConditionOfBoundaryConditionalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.condition(""))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ConditionalEventDefinition.class,
            "Conditional event must contain an expression for evaluation"));
  }

  @Test
  @DisplayName("A intermediate conditional event contain a valid expression for evaluation")
  void verifyValidConditionOfIntermediateConditionalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .intermediateCatchEvent("catch", i -> i.condition(CONDITION))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A intermediate conditional event must contain a valid expression for evaluation")
  void verifyInvalidConditionOfIntermediateConditionalEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .intermediateCatchEvent("catch", i -> i.condition(""))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ConditionalEventDefinition.class,
            "Conditional event must contain an expression for evaluation"));
  }

  @Test
  @DisplayName(
      "An event subprocess conditional start event contain a valid expression for evaluation")
  void verifyValidConditionOfEventSubprocessConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub", e -> e.startEvent("start", s -> s.condition(CONDITION).endEvent()))
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName(
      "An event subprocess conditional start event must contain a valid expression for evaluation")
  void verifyInvalidConditionOfEventSubprocessConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess("sub", e -> e.startEvent("start", s -> s.condition("").endEvent()))
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ConditionalEventDefinition.class,
            "Conditional event must contain an expression for evaluation"));
  }

  @Test
  @DisplayName(
      "Cannot have more than one conditional event subscription with the same condition for event subprocess conditional start events")
  void verifyNoDuplicatedConditionOfEventSubprocessConditionalStartEvents() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub-1", e -> e.startEvent("start-1", s -> s.condition(CONDITION).endEvent()))
            .eventSubProcess(
                "sub-2", e -> e.startEvent("start-2", s -> s.condition(CONDITION).endEvent()))
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Cannot have more than one conditional event subscription with the same condition '= true'"));
  }
}
