/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSendTask;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;

public final class SendTaskProcessor extends JobWorkerTaskSupportingProcessor<ExecutableSendTask> {

  private static final Either<Failure, ?> UNSUPPORTED_IMPLEMENTATION_RESULT =
      Either.left(
          new Failure(
              "Currently, only job worker-based implementation is supported for 'sendTask'. "
                  + "To recover, model the task using a 'zeebe:taskDefinition' with a valid job type, "
                  + "deploy the updated process version, and migrate the instance to it.",
              ErrorType.UNKNOWN));

  public SendTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Class<ExecutableSendTask> getType() {
    return ExecutableSendTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableSendTask element, final BpmnElementContext context) {
    final var jobWorkerProperties = element.getJobWorkerProperties();
    return jobWorkerProperties != null && jobWorkerProperties.getType() != null;
  }

  @Override
  protected Either<Failure, ?> onActivateInternal(
      final ExecutableSendTask element, final BpmnElementContext context) {
    return UNSUPPORTED_IMPLEMENTATION_RESULT;
  }

  @Override
  protected Either<Failure, ?> onFinalizeActivationInternal(
      final ExecutableSendTask element, final BpmnElementContext context) {
    return UNSUPPORTED_IMPLEMENTATION_RESULT;
  }

  @Override
  protected Either<Failure, ?> onCompleteInternal(
      final ExecutableSendTask element, final BpmnElementContext context) {
    return UNSUPPORTED_IMPLEMENTATION_RESULT;
  }

  @Override
  protected Either<Failure, ?> onFinalizeCompletionInternal(
      final ExecutableSendTask element, final BpmnElementContext context) {
    return UNSUPPORTED_IMPLEMENTATION_RESULT;
  }

  @Override
  protected TransitionOutcome onTerminateInternal(
      final ExecutableSendTask element, final BpmnElementContext context) {
    return TransitionOutcome.CONTINUE;
  }
}
