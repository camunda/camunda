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
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.CancelEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ZeebeEndEventValidationTest {

  private static final String END_EVENT_ID = "end";
  private static final String EVENT_DEFINITION_ID = "event-definition-id";

  @ParameterizedTest(name = "[{index}] event type = {0}")
  @MethodSource("supportedEndEventTypes")
  @DisplayName("Should support end event of the given type")
  void supportedEndEventTypes(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process = createProcessWithEndEvent(elementBuilder);

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest(name = "[{index}] event type = {0}")
  @MethodSource("unsupportedEndEventTypes")
  @DisplayName("Should not support end event of the given type")
  void unsupportedEndEventTypes(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process = createProcessWithEndEvent(elementBuilder);

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            END_EVENT_ID,
            "End events must be one of: none, error, message, terminate, signal, escalation or compensation"),
        expect(EVENT_DEFINITION_ID, "Event definition of this type is not supported"));
  }

  @Test
  @DisplayName("An end event should not have an outgoing sequence flow")
  void outgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent(END_EVENT_ID)
            // an activity after the end event
            .manualTask()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            EndEvent.class, "End events must not have outgoing sequence flows to other elements."));
  }

  private static BpmnModelInstance createProcessWithEndEvent(
      final BpmnElementBuilder elementBuilder) {
    final StartEventBuilder processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    return elementBuilder.build(processBuilder).done();
  }

  private static Stream<BpmnElementBuilder> supportedEndEventTypes() {
    return Stream.of(
        BpmnElementBuilder.of("none", builder -> builder.endEvent(END_EVENT_ID)),
        BpmnElementBuilder.of(
            "signal", builder -> builder.endEvent(END_EVENT_ID).signal("signal-name")),
        BpmnElementBuilder.of(
            "error", builder -> builder.endEvent(END_EVENT_ID).error("error-code")),
        BpmnElementBuilder.of(
            "escalation", builder -> builder.endEvent(END_EVENT_ID).escalation("escalation-code")),
        BpmnElementBuilder.of(
            "message (job worker)",
            builder ->
                builder.endEvent(END_EVENT_ID).message("message-name").zeebeJobType("job-type")),
        BpmnElementBuilder.of(
            "message",
            builder ->
                builder
                    .endEvent(END_EVENT_ID)
                    .message(b -> b.name("message-name").zeebeCorrelationKey("correlationKey"))),
        BpmnElementBuilder.of("termination", builder -> builder.endEvent(END_EVENT_ID).terminate()),
        BpmnElementBuilder.of(
            "compensation",
            builder ->
                builder
                    .endEvent(END_EVENT_ID)
                    .compensateEventDefinition()
                    .compensateEventDefinitionDone()));
  }

  private static Stream<BpmnElementBuilder> unsupportedEndEventTypes() {
    return Stream.of(
        BpmnElementBuilder.of(
            "cancel",
            builder -> {
              final EndEventBuilder endEvent = builder.endEvent(END_EVENT_ID);
              // currently, we don't have a builder for cancel events
              final CancelEventDefinition cancelEventDefinition =
                  endEvent.getElement().getModelInstance().newInstance(CancelEventDefinition.class);
              cancelEventDefinition.setId(EVENT_DEFINITION_ID);
              endEvent.getElement().getEventDefinitions().add(cancelEventDefinition);
              return endEvent;
            }));
  }
}
