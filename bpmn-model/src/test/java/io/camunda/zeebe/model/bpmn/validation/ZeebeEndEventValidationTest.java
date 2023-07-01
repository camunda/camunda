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
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TerminateEventDefinition;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ZeebeEndEventValidationTest {

  private static final String END_EVENT_ID = "end";

  @ParameterizedTest(name = "[{index}] event type = {0}")
  @MethodSource("supportedEndEventTypes")
  @DisplayName("Should support end event of the given type")
  void supportedEndEventTypes(final EndEventTypeBuilder endEventTypeBuilder) {
    // given
    final BpmnModelInstance process = createProcessWithEndEvent(endEventTypeBuilder);

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest(name = "[{index}] event type = {0}")
  @MethodSource("unsupportedEndEventTypes")
  @DisplayName("Should not support end event of the given type")
  void unsupportedEndEventTypes(final EndEventTypeBuilder endEventTypeBuilder) {
    // given
    final BpmnModelInstance process = createProcessWithEndEvent(endEventTypeBuilder);

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            END_EVENT_ID,
            "End events must be one of: none, error, message, terminate, signal, or escalation"),
        expect(endEventTypeBuilder.eventType, "Event definition of this type is not supported"));
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
      final EndEventTypeBuilder endEventTypeBuilder) {
    final StartEventBuilder processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    endEventTypeBuilder.build(processBuilder.endEvent(END_EVENT_ID));
    return processBuilder.done();
  }

  private static Stream<EndEventTypeBuilder> supportedEndEventTypes() {
    return Stream.of(
        new EndEventTypeBuilder(null, endEvent -> endEvent),
        new EndEventTypeBuilder(
            SignalEventDefinition.class, endEvent -> endEvent.signal("signal-name")),
        new EndEventTypeBuilder(
            ErrorEventDefinition.class, endEvent -> endEvent.error("error-code")),
        new EndEventTypeBuilder(
            EscalationEventDefinition.class, endEvent -> endEvent.escalation("escalation-code")),
        new EndEventTypeBuilder(
            MessageEventDefinition.class,
            endEvent -> endEvent.message("message-name").zeebeJobType("job-type")),
        new EndEventTypeBuilder(
            MessageEventDefinition.class,
            endEvent ->
                endEvent.message(
                    b -> b.name("message-name").zeebeCorrelationKey("correlationKey"))),
        new EndEventTypeBuilder(TerminateEventDefinition.class, EndEventBuilder::terminate));
  }

  private static Stream<EndEventTypeBuilder> unsupportedEndEventTypes() {
    return Stream.of(
        new EndEventTypeBuilder(
            CompensateEventDefinition.class,
            endEvent -> endEvent.compensateEventDefinition().compensateEventDefinitionDone()),
        new EndEventTypeBuilder(
            CancelEventDefinition.class,
            endEvent -> {
              // currently, we don't have a builder for cancel events
              final CancelEventDefinition cancelEventDefinition =
                  endEvent.getElement().getModelInstance().newInstance(CancelEventDefinition.class);
              endEvent.getElement().getEventDefinitions().add(cancelEventDefinition);
              return endEvent;
            }));
  }

  private static final class EndEventTypeBuilder {
    private final String eventTypeName;
    private final Class<? extends EventDefinition> eventType;
    private final UnaryOperator<EndEventBuilder> elementModifier;

    private EndEventTypeBuilder(
        final Class<? extends EventDefinition> eventType,
        final UnaryOperator<EndEventBuilder> elementModifier) {
      this.eventType = eventType;
      eventTypeName = eventType == null ? "none" : eventType.getSimpleName();
      this.elementModifier = elementModifier;
    }

    public EndEventBuilder build(final EndEventBuilder endEventBuilder) {
      return elementModifier.apply(endEventBuilder);
    }

    @Override
    public String toString() {
      return eventTypeName;
    }
  }
}
