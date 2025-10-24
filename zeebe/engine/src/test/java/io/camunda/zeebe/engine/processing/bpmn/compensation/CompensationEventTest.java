/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.compensation;

import io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ProcessValidationUtil;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractStartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.Task;
import org.junit.jupiter.api.Test;

public class CompensationEventTest {

  @Test
  public void shouldDeployCompensationBoundaryEvent() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-boundary-event.bpmn");

    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  public void shouldNotDeployCompensationBoundaryEventWithoutAssociation() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-boundary-event-no-association.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            BoundaryEvent.class,
            "Compensation boundary events must have a compensation association and no outgoing sequence flows"));
  }

  @Test
  public void shouldDeployCompensationIntermediateThrowEvent() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-event.bpmn");

    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  public void shouldNotDeployCompensationIntermediateThrowEventWithWaitForCompletionFalse() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-throw-event-attribute-false.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            IntermediateThrowEvent.class,
            "A compensation intermediate throwing event waitForCompletion attribute must be true or not present"));
  }

  @Test
  public void shouldDeployCompensationEndEvent() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-end-event.bpmn");

    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  public void shouldNotDeployCompensationEndEventWithWaitForCompletionFalse() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-end-event-attribute-false.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            EndEvent.class,
            "A compensation end event waitForCompletion attribute must be true or not present"));
  }

  @Test
  public void shouldDeployCompensationUndefinedTask() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-undefined-task.bpmn");

    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  public void shouldNotDeployCompensationHandlerWithOutgoingFlow() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-task-with-outgoing.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Task.class, "A compensation handler should have no outgoing sequence flows"));
  }

  @Test
  public void shouldNotDeployCompensationHandlerWithIncomingFlow() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-task-with-incoming.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Task.class, "A compensation handler should have no incoming sequence flows"));
  }

  @Test
  public void shouldNotDeployCompensationHandlerWithBoundaryEvent() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-task-with-boundary.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Task.class, "A compensation handler should have no boundary events"));
  }

  @Test
  public void shouldNotDeployCompensationEventSubprocess() {
    final var process =
        Bpmn.createExecutableProcess("compensation-process")
            .startEvent()
            .subProcess(
                "embedded-subprocess",
                s ->
                    s.embeddedSubProcess()
                        .eventSubProcess(
                            "subprocess",
                            eventSubProcess ->
                                eventSubProcess
                                    .startEvent(
                                        "compensation-start",
                                        AbstractStartEventBuilder::compensation)
                                    .intermediateThrowEvent(
                                        "compensation-throw-event",
                                        AbstractThrowEventBuilder::compensateEventDefinition)
                                    .userTask("B")
                                    .endEvent())
                        .startEvent()
                        .userTask("A")
                        .boundaryEvent()
                        .compensation(c -> c.userTask("undo-A"))
                        .endEvent()
                        .done())
            .endEvent(
                "compensation-end-event",
                end -> end.compensateEventDefinition().compensateEventDefinitionDone())
            .done();

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            SubProcess.class,
            "Start events in event subprocesses must be one of: message, timer, error, signal or escalation"));
  }

  @Test
  public void shouldNotDeployCompensationEventSubprocessOnProcessLevel() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-not-embedded-subprocess.bpmn");

    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            SubProcess.class,
            "A compensation event subprocess is not allowed on the process level"));
  }

  private BpmnModelInstance createModelFromClasspathResource(final String classpath) {
    final var resourceAsStream = getClass().getResourceAsStream(classpath);
    return Bpmn.readModelFromStream(resourceAsStream);
  }
}
