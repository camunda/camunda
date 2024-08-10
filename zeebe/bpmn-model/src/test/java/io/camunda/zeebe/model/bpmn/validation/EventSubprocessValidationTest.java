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
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventSubprocessValidationTest {

  private static final String PROCESS_ID = "process";
  private static final String SUBPROCESS_ID = "subprocess";

  @ParameterizedTest
  @MethodSource("supportedStartEvents")
  @DisplayName("An event subprocess with a supported start event should be valid")
  void validStartEvent(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process = process(elementBuilder);

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName(
      "An event subprocess with a compensation start event should not be valid on process level")
  void compensationStartEventOnProcessLevel() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocessBuilder =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .compensateEventDefinition()
                .compensateEventDefinitionDone();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(SUBPROCESS_ID, eventSubprocessBuilder)
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SUBPROCESS_ID,
            "Start events in event subprocesses must be one of: message, timer, error, signal or escalation"),
        expect(
            SUBPROCESS_ID, "A compensation event subprocess is not allowed on the process level"));
  }

  @Test
  @DisplayName(
      "An event subprocess with a compensation start event should not be valid inside a subprocess")
  void compensationStartEventInsideSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocessBuilder =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .compensateEventDefinition()
                .compensateEventDefinitionDone();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess()
            .embeddedSubProcess()
            .eventSubProcess(SUBPROCESS_ID, eventSubprocessBuilder)
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SUBPROCESS_ID,
            "Start events in event subprocesses must be one of: message, timer, error, signal or escalation"));
  }

  @Test
  @DisplayName("An event subprocess with a conditional start event should not be valid")
  void conditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                SUBPROCESS_ID, eventSubprocess -> eventSubprocess.startEvent().condition("true"))
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            SUBPROCESS_ID,
            "Start events in event subprocesses must be one of: message, timer, error, signal or escalation"),
        expect(ConditionalEventDefinition.class, "Event definition of this type is not supported"));
  }

  @Test
  @DisplayName("An event subprocess without a start event should not be valid")
  void withoutStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(SUBPROCESS_ID, eventSubprocess -> {})
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(SUBPROCESS_ID, "Must have exactly one start event"));
  }

  @Test
  @DisplayName("An event subprocess with multiple start events should not be valid")
  void multipleStartEvents() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                SUBPROCESS_ID,
                eventSubprocess -> {
                  eventSubprocess.startEvent().signal("a").endEvent();
                  eventSubprocess.startEvent().signal("b").endEvent();
                })
            .startEvent()
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(SUBPROCESS_ID, "Must have exactly one start event"));
  }

  private BpmnModelInstance process(final BpmnElementBuilder elementBuilder) {

    final AbstractFlowNodeBuilder<?, ?> processBuilder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent();

    return elementBuilder.build(processBuilder).done();
  }

  private static StartEventBuilder withEventSubprocess(
      final AbstractFlowNodeBuilder<?, ?> builder) {
    return builder.moveToProcess(PROCESS_ID).eventSubProcess(SUBPROCESS_ID).startEvent();
  }

  private static Stream<BpmnElementBuilder> supportedStartEvents() {
    return Stream.of(
        BpmnElementBuilder.of(
            "timer start event", builder -> withEventSubprocess(builder).timerWithDuration("P1D")),
        BpmnElementBuilder.of(
            "message start event",
            builder ->
                withEventSubprocess(builder)
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))),
        BpmnElementBuilder.of("error start event", builder -> withEventSubprocess(builder).error()),
        BpmnElementBuilder.of(
            "signal start event", builder -> withEventSubprocess(builder).signal("signal")),
        BpmnElementBuilder.of(
            "escalation start event", builder -> withEventSubprocess(builder).escalation()));
  }
}
