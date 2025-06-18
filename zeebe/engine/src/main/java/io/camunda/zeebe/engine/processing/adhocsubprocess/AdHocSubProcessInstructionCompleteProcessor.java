/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnAdHocSubProcessBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class AdHocSubProcessInstructionCompleteProcessor
    implements TypedRecordProcessor<AdHocSubProcessInstructionRecord> {

  private final BpmnAdHocSubProcessBehavior adHocSubProcessBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final ElementInstanceState elementInstanceState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public AdHocSubProcessInstructionCompleteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BpmnAdHocSubProcessBehavior adHocSubProcessBehavior) {
    this.adHocSubProcessBehavior = adHocSubProcessBehavior;
    this.authCheckBehavior = authCheckBehavior;
    elementInstanceState = processingState.getElementInstanceState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<AdHocSubProcessInstructionRecord> command) {

    final AdHocSubProcessInstructionRecord instructionRecord = command.getValue();
    final long adHocSubProcessInstanceKey =
        Long.parseLong(instructionRecord.getAdHocSubProcessInstanceKey());
    final var adHocSubProcessInstance =
        elementInstanceState.getInstance(adHocSubProcessInstanceKey);

    validateCommand(command, adHocSubProcessInstance)
        .ifRightOrLeft(
            ok -> {
              final var context = createBpmnElementContext(adHocSubProcessInstance);
              adHocSubProcessBehavior.completionConditionFulfilled(
                  context, instructionRecord.isCancelRemainingInstances());

              stateWriter.appendFollowUpEvent(
                  command.getKey(), AdHocSubProcessInstructionIntent.COMPLETED, instructionRecord);
              responseWriter.writeEventOnCommand(
                  command.getKey(),
                  AdHocSubProcessInstructionIntent.COMPLETED,
                  instructionRecord,
                  command);
            },
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  private Either<Rejection, Void> validateCommand(
      final TypedRecord<AdHocSubProcessInstructionRecord> record,
      final ElementInstance adHocSubProcessInstance) {

    final String adHocSubProcessInstanceKey = record.getValue().getAdHocSubProcessInstanceKey();

    if (adHocSubProcessInstance == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              "No element instance found with key '%s'".formatted(adHocSubProcessInstanceKey)));
    }

    if (adHocSubProcessInstance.getValue().getBpmnElementType()
        != BpmnElementType.AD_HOC_SUB_PROCESS) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "The element instance with key '%s' is not an ad-hoc sub-process."
                  .formatted(adHocSubProcessInstanceKey)));
    }

    if (!adHocSubProcessInstance.isActive()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              "The element instance with key '%s' is not active"
                  .formatted(adHocSubProcessInstanceKey)));
    }

    return authorize(record, adHocSubProcessInstance);
  }

  private Either<Rejection, Void> authorize(
      final TypedRecord<AdHocSubProcessInstructionRecord> command,
      final ElementInstance adHocSubProcessElementInstance) {
    final var authRequest =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                adHocSubProcessElementInstance.getValue().getTenantId())
            .addResourceId(adHocSubProcessElementInstance.getValue().getBpmnProcessId());

    return authCheckBehavior.isAuthorized(authRequest);
  }

  private static BpmnElementContext createBpmnElementContext(
      final ElementInstance elementInstance) {

    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }
}
