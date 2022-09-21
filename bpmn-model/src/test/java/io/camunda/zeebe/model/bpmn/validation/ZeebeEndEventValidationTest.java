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
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZeebeEndEventValidationTest {

  @Test
  @DisplayName("Should support terminate end events")
  void terminateEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("end", EndEventBuilder::terminate)
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Should not support signal end events")
  void signalEventEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("end")
            .signalEventDefinition("foo")
            .id("eventDefinition")
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect("end", "End events must be one of: none, error, message, or terminate"),
        expect("eventDefinition", "Event definition of this type is not supported"));
  }

  @Test
  @DisplayName("An end event should not have an outgoing sequence flow")
  void outgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("end")
            // an activity after the end event
            .manualTask()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            EndEvent.class, "End events must not have outgoing sequence flows to other elements."));
  }
}
