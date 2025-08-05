/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnAdHocSubProcessBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivateElementInstruction;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue.AdHocSubProcessActivateElementInstructionValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class AdHocSubProcessInstructionActivateProcessor
    implements TypedRecordProcessor<AdHocSubProcessInstructionRecord> {

  private static final String ERROR_MSG_AD_HOC_SUB_PROCESS_NOT_FOUND =
      "Expected to activate activities for ad-hoc sub-process but no ad-hoc sub-process instance found with key '%s'.";
  private static final String ERROR_MSG_DUPLICATE_ACTIVITIES =
      "Expected to activate activities for ad-hoc sub-process with key '%s', but duplicate activities were given.";
  private static final String ERROR_MSG_AD_HOC_SUB_PROCESS_IS_NO_ACTIVE =
      "Expected to activate activities for ad-hoc sub-process with key '%s', but it is not active.";
  private static final String ERROR_MSG_AD_HOC_SUB_PROCESS_IS_NOT_ACTIVE =
      "Expected to activate activities for ad-hoc sub-process with key '%s', but it is not active.";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final BpmnAdHocSubProcessBehavior bpmnAdHocSubProcessBehavior;

  public AdHocSubProcessInstructionActivateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    this.authCheckBehavior = authCheckBehavior;
    bpmnAdHocSubProcessBehavior = bpmnBehaviors.adHocSubProcessBehavior();
  }

  @Override
  public void processRecord(final TypedRecord<AdHocSubProcessInstructionRecord> command) {
    final var adHocSubProcessElementInstance =
        elementInstanceState.getInstance(command.getValue().getAdHocSubProcessInstanceKey());
    if (adHocSubProcessElementInstance == null) {
      writeRejectionError(
          command,
          RejectionType.NOT_FOUND,
          String.format(
              ERROR_MSG_AD_HOC_SUB_PROCESS_NOT_FOUND,
              command.getValue().getAdHocSubProcessInstanceKey()));

      return;
    }

    if (!adHocSubProcessElementInstance.isActive()) {
      writeRejectionError(
          command,
          RejectionType.INVALID_STATE,
          String.format(
              ERROR_MSG_AD_HOC_SUB_PROCESS_IS_NO_ACTIVE,
              command.getValue().getAdHocSubProcessInstanceKey()));

      return;
    }

    final var authResult = authorize(command, adHocSubProcessElementInstance);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? ERROR_MSG_AD_HOC_SUB_PROCESS_NOT_FOUND.formatted(
                  command.getValue().getAdHocSubProcessInstanceKey())
              : rejection.reason();
      writeRejectionError(command, rejection.type(), errorMessage);

      return;
    }

    if (hasDuplicateElementsToActivate(command)) {
      writeRejectionError(
          command,
          RejectionType.INVALID_ARGUMENT,
          String.format(
              ERROR_MSG_DUPLICATE_ACTIVITIES, command.getValue().getAdHocSubProcessInstanceKey()));

      return;
    }

    if (!adHocSubProcessElementInstance.isActive()) {
      writeRejectionError(
          command,
          RejectionType.INVALID_STATE,
          String.format(
              ERROR_MSG_AD_HOC_SUB_PROCESS_IS_NOT_ACTIVE,
              command.getValue().getAdHocSubProcessInstanceKey()));

      return;
    }

    final var adHocSubProcessDefinition =
        processState
            .getProcessByKeyAndTenant(
                adHocSubProcessElementInstance.getValue().getProcessDefinitionKey(),
                adHocSubProcessElementInstance.getValue().getTenantId())
            .getProcess();
    final var adHocSubProcessElement =
        (ExecutableAdHocSubProcess)
            adHocSubProcessDefinition.getElementById(
                adHocSubProcessElementInstance.getValue().getElementId());

    // check that the given elements exist within the ad-hoc sub-process
    final var activateElements =
        command.getValue().activateElements().stream()
            .map(AdHocSubProcessActivateElementInstruction::getElementId)
            .toList();
    final var activateElementsAreInProcess =
        AdHocSubProcessUtils.validateActivateElementsExistInAdHocSubProcess(
            adHocSubProcessElementInstance.getKey(), adHocSubProcessElement, activateElements);
    if (activateElementsAreInProcess.isLeft()) {
      final var rejection = activateElementsAreInProcess.getLeft();
      writeRejectionError(command, rejection.type(), rejection.reason());
      return;
    }

    final var bpmnElementContext = new BpmnElementContextImpl();
    bpmnElementContext.init(
        adHocSubProcessElementInstance.getKey(),
        adHocSubProcessElementInstance.getValue(),
        adHocSubProcessElementInstance.getState());

    // activate the elements
    for (final var elementValue : command.getValue().activateElements()) {
      bpmnAdHocSubProcessBehavior.activateElement(
          adHocSubProcessElement,
          bpmnElementContext,
          elementValue.getElementId(),
          elementValue.getVariablesBuffer());
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), AdHocSubProcessInstructionIntent.ACTIVATED, command.getValue());

    responseWriter.writeEventOnCommand(
        command.getKey(), AdHocSubProcessInstructionIntent.ACTIVATED, command.getValue(), command);
  }

  private void writeRejectionError(
      final TypedRecord<AdHocSubProcessInstructionRecord> command,
      final RejectionType rejectionType,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, rejectionType, errorMessage);
    responseWriter.writeRejectionOnCommand(command, rejectionType, errorMessage);
  }

  private boolean hasDuplicateElementsToActivate(
      final TypedRecord<AdHocSubProcessInstructionRecord> command) {
    return command.getValue().getActivateElements().stream()
            .map(AdHocSubProcessActivateElementInstructionValue::getElementId)
            .distinct()
            .count()
        != command.getValue().getActivateElements().size();
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
}
