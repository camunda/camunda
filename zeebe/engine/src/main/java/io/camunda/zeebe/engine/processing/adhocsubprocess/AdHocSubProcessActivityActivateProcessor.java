/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationElement;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue.AdHocSubProcessActivityActivationElementValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

public class AdHocSubProcessActivityActivateProcessor
    implements TypedRecordProcessor<AdHocSubProcessActivityActivationRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedCommandWriter commandWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;

  public AdHocSubProcessActivityActivateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    commandWriter = writers.command();
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<AdHocSubProcessActivityActivationRecord> command) {
    final var adHocSubprocessElementInstance =
        elementInstanceState.getInstance(
            Long.parseLong(command.getValue().getAdHocSubProcessInstanceKey()));
    if (adHocSubprocessElementInstance == null) {
      throw new AdHocSubProcessInstanceIsNullException(
          command.getValue().getAdHocSubProcessInstanceKey());
    }

    if (!isAuthorized(command, adHocSubprocessElementInstance)) {
      return;
    }

    validateDuplicateElements(command);

    final var adHocSubprocessElementId = adHocSubprocessElementInstance.getValue().getElementId();
    if (adHocSubprocessElementInstance.isInFinalState()) {
      throw new AdHocSubProcessInFinalStateException(adHocSubprocessElementId);
    }

    if (!adHocSubprocessElementInstance.isActive()) {
      throw new AdHocSubProcessInstanceNotActivatedException(
          command.getValue().getAdHocSubProcessInstanceKey());
    }

    final var adHocSubprocessDefinition =
        processState
            .getProcessByKeyAndTenant(
                adHocSubprocessElementInstance.getValue().getProcessDefinitionKey(),
                adHocSubprocessElementInstance.getValue().getTenantId())
            .getProcess();

    final var adHocSubprocessElement =
        adHocSubprocessDefinition.getElementById(
            adHocSubprocessElementInstance.getValue().getElementId());
    final var adHocActivitiesById =
        ((ExecutableAdHocSubProcess) adHocSubprocessElement).getAdHocActivitiesById();

    // check that the given elements exist within the ad-hoc subprocess
    final var elementsNotInAdHocSubProcess =
        command.getValue().elements().stream()
            .map(AdHocSubProcessActivityActivationElement::getElementId)
            .filter(elementId -> !adHocActivitiesById.containsKey(elementId))
            .toList();
    if (!elementsNotInAdHocSubProcess.isEmpty()) {
      throw new ElementNotFoundException(adHocSubprocessElementId, elementsNotInAdHocSubProcess);
    }

    // activate the elements
    for (final var elementValue : command.getValue().getElements()) {
      final var elementToActivate =
          adHocSubprocessDefinition.getElementById(elementValue.getElementId());
      final var elementProcessInstanceRecord = new ProcessInstanceRecord();
      elementProcessInstanceRecord.wrap(adHocSubprocessElementInstance.getValue());
      elementProcessInstanceRecord
          .setFlowScopeKey(adHocSubprocessElementInstance.getKey())
          .setElementId(elementToActivate.getId())
          .setBpmnElementType(elementToActivate.getElementType())
          .setBpmnEventType(elementToActivate.getEventType());

      final long elementToActivateInstanceKey = keyGenerator.nextKey();
      commandWriter.appendFollowUpCommand(
          elementToActivateInstanceKey,
          ProcessInstanceIntent.ACTIVATE_ELEMENT,
          elementProcessInstanceRecord);
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), AdHocSubProcessActivityActivationIntent.ACTIVATED, command.getValue());

    responseWriter.writeEventOnCommand(
        command.getKey(),
        AdHocSubProcessActivityActivationIntent.ACTIVATED,
        command.getValue(),
        command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<AdHocSubProcessActivityActivationRecord> command, final Throwable error) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, error.getMessage());
    responseWriter.writeRejectionOnCommand(
        command, RejectionType.INVALID_ARGUMENT, error.getMessage());

    return ProcessingError.EXPECTED_ERROR;
  }

  private void validateDuplicateElements(
      final TypedRecord<AdHocSubProcessActivityActivationRecord> command) {
    final var hasDuplicates =
        command.getValue().getElements().stream()
                .map(AdHocSubProcessActivityActivationElementValue::getElementId)
                .distinct()
                .count()
            != command.getValue().getElements().size();
    if (hasDuplicates) {
      throw new DuplicateElementsException(
          command.getValue().getElements().stream()
              .map(AdHocSubProcessActivityActivationElementValue::getElementId)
              .toList());
    }
  }

  private boolean isAuthorized(
      final TypedRecord<AdHocSubProcessActivityActivationRecord> command,
      final ElementInstance adHocSubprocessElementInstance) {
    final var authRequest =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                adHocSubprocessElementInstance.getValue().getTenantId())
            .addResourceId(adHocSubprocessElementInstance.getValue().getBpmnProcessId());
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "modify a process instance",
                  command.getValue().getAdHocSubProcessInstanceKey(),
                  "such process instance")
              : rejection.reason();
      rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
      responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
      return false;
    }

    return true;
  }

  private static final class DuplicateElementsException extends IllegalStateException {
    private static final String ERROR_MESSAGE = "Duplicate elements %s not allowed.";

    private DuplicateElementsException(final List<String> elementIds) {
      super(String.format(ERROR_MESSAGE, elementIds));
    }
  }

  private static final class ElementNotFoundException extends IllegalStateException {
    private static final String ERROR_MESSAGE =
        "Elements %s do not exist in ad-hoc subprocess <%s>";

    private ElementNotFoundException(
        final String adHocSubprocessId, final List<String> elementIds) {
      super(String.format(ERROR_MESSAGE, elementIds, adHocSubprocessId));
    }
  }

  private static final class AdHocSubProcessInFinalStateException extends IllegalStateException {
    private static final String ERROR_MESSAGE =
        "Ad-hoc subprocess <%s> is already in a terminal state. Cannot activate any further activities.";

    private AdHocSubProcessInFinalStateException(final String adHocSubprocessElementId) {
      super(String.format(ERROR_MESSAGE, adHocSubprocessElementId));
    }
  }

  private static final class AdHocSubProcessInstanceIsNullException extends IllegalStateException {
    private static final String ERROR_MESSAGE = "Ad-hoc subprocess instance <%s> is.";

    private AdHocSubProcessInstanceIsNullException(final String adHocSubprocessInstanceKey) {
      super(String.format(ERROR_MESSAGE, adHocSubprocessInstanceKey));
    }
  }

  private static final class AdHocSubProcessInstanceNotActivatedException
      extends IllegalStateException {
    private static final String ERROR_MESSAGE = "Ad-hoc subprocess instance <%s> is.";

    private AdHocSubProcessInstanceNotActivatedException(final String adHocSubprocessInstanceKey) {
      super(String.format(ERROR_MESSAGE, adHocSubprocessInstanceKey));
    }
  }
}
