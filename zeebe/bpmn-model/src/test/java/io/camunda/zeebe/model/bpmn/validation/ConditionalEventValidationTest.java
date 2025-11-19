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
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConditionalEventValidationTest {

  @Test
  @DisplayName("Conditional boundary event with condition should be valid")
  void validConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary", b -> b.condition("= status = \"active\""))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Conditional intermediate catch event with condition should be valid")
  void validConditionalIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch", e -> e.condition("= count > 5"))
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Conditional start event in event subprocess should be valid")
  void validConditionalEventSubprocessStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "subprocess",
                eventSubprocess ->
                    eventSubprocess.startEvent("start").condition("= error = true").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Conditional root-level start event should be valid")
  void validConditionalRootLevelStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition("= triggered = true")
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Conditional event with empty condition should be invalid")
  void invalidConditionalEventWithEmptyCondition() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary", b -> b.condition("   "))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ConditionalEventDefinition.class, "Condition expression must not be empty"));
  }

  @Test
  @DisplayName("Conditional event with malformed expression should be invalid")
  void invalidConditionalEventWithMalformedExpression() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary", b -> b.condition("x >>>"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ConditionalEventDefinition.class, "Invalid condition expression syntax"));
  }

  @Test
  @DisplayName("Conditional event with unbalanced parentheses should be invalid")
  void invalidConditionalEventWithUnbalancedParentheses() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary", b -> b.condition("(x > 5"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ConditionalEventDefinition.class,
            "Invalid condition expression: unbalanced parentheses"));
  }
}
