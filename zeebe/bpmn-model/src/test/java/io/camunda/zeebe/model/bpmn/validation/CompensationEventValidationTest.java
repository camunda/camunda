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
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompensationEventValidationTest {

  private static final String COMPENSATION_EVENT_DEFINITION_ID = "compensation-event-definition";

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event don't need to reference an activity")
  void noActivityRef(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess().startEvent(),
            elementBuilder,
            compensationEventDefinition -> {});

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event should reference an existing activity")
  void activityRefNonExisting(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess().startEvent(),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "non-existing"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CompensateEventDefinition.class,
            "The referenced compensation activity 'non-existing' doesn't exist"));
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName(
      "A compensation throw event can reference an activity with a compensation boundary event")
  void activityRefToCompensationHandler(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess()
                .startEvent()
                .userTask(
                    "task",
                    userTask ->
                        userTask
                            .boundaryEvent()
                            .compensation(compensation -> compensation.userTask("undo-task"))),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event can reference a subprocess")
  void activityRefToSubprocess(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess()
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess -> subprocess.embeddedSubProcess().startEvent().endEvent()),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "subprocess"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event should reference a compensation handler")
  void activityRefToNonCompensationHandler(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess().startEvent().userTask("task"),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CompensateEventDefinition.class,
            "The referenced compensation activity 'task' must have either a compensation boundary event or be a subprocess"));
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event should reference an activity in the same scope")
  void activityRefToActivityOutOfScope(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess()
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess ->
                        subprocess
                            .embeddedSubProcess()
                            .startEvent()
                            .userTask("task")
                            .boundaryEvent()
                            .compensation(compensation -> compensation.userTask("undo-task"))),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CompensateEventDefinition.class,
            "The referenced compensation activity 'task' must be in the same scope as the compensation throw event"));
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event can reference an activity from an event subprocess")
  void activityRefFromEventSubprocess(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask(
                    "task",
                    userTask ->
                        userTask
                            .boundaryEvent()
                            .compensation(compensation -> compensation.userTask("undo-task")))
                .moveToProcess("process")
                .eventSubProcess("event-subprocess")
                .startEvent()
                .error(),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName("A compensation throw event can reference an activity inside an event subprocess")
  void activityRefInsideEventSubprocess(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .endEvent()
                .moveToProcess("process")
                .eventSubProcess("event-subprocess")
                .startEvent()
                .error()
                .userTask(
                    "task",
                    userTask ->
                        userTask
                            .boundaryEvent()
                            .compensation(compensation -> compensation.userTask("undo-task"))),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("compensationThrowEvents")
  @DisplayName(
      "A compensation throw event should reference an activity in the scope of an event subprocess")
  void activityRefFromEventSubprocessOutOfScope(final BpmnElementBuilder elementBuilder) {
    // given
    final BpmnModelInstance process =
        processWithCompensationThrowEvent(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess ->
                        subprocess
                            .embeddedSubProcess()
                            .startEvent()
                            .userTask("task")
                            .boundaryEvent()
                            .compensation(compensation -> compensation.userTask("undo-task")))
                .moveToProcess("process")
                .eventSubProcess("event-subprocess")
                .startEvent()
                .error(),
            elementBuilder,
            compensationEventDefinition ->
                compensationEventDefinition.setAttributeValue("activityRef", "task"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            CompensateEventDefinition.class,
            "The referenced compensation activity 'task' must be in the same scope as the compensation throw event"));
  }

  private BpmnModelInstance processWithCompensationThrowEvent(
      final AbstractFlowNodeBuilder<?, ?> processBuilder,
      final BpmnElementBuilder elementBuilder,
      final Consumer<CompensateEventDefinition> consumer) {

    final BpmnModelInstance process = elementBuilder.build(processBuilder).done();

    final CompensateEventDefinition compensateEventDefinition =
        process.getModelElementById(COMPENSATION_EVENT_DEFINITION_ID);
    consumer.accept(compensateEventDefinition);

    return process;
  }

  private static Stream<BpmnElementBuilder> compensationThrowEvents() {
    return Stream.of(
        BpmnElementBuilder.of(
            "intermediate throw event",
            builder ->
                builder.intermediateThrowEvent(
                    "compensation-event",
                    event -> event.compensateEventDefinition(COMPENSATION_EVENT_DEFINITION_ID))),
        BpmnElementBuilder.of(
            "end event",
            builder ->
                builder.endEvent(
                    "compensation-event",
                    event -> event.compensateEventDefinition(COMPENSATION_EVENT_DEFINITION_ID))));
  }
}
