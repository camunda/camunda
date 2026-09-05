/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;

public final class ProcessInstanceResumeProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private static final String MESSAGE_PREFIX =
      "Expected to resume a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";
  private static final String PROCESS_NOT_SUSPENDED_MESSAGE =
      MESSAGE_PREFIX + "it is not currently suspended";

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CslAuthorizationCheck cslCheck;
  private final SuspensionState suspensionState;
  private final SuspensionMetrics suspensionMetrics;

  public ProcessInstanceResumeProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final SuspensionMetrics suspensionMetrics) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.cslCheck = cslCheck;
    suspensionState = processingState.getSuspensionState();
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    validateNotFound(command, elementInstance)
        .flatMap(ei -> validateAuthorized(command, ei))
        .flatMap(ei -> validateSuspensionState(command, ei))
        .ifRightOrLeft(
            ei -> resume(command, ei),
            rejection -> {
              if (elementInstance != null) {
                enrichRejectionCommand(command, elementInstance.getValue());
              }
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionBehavior.PROCESS;
  }

  private Either<Rejection, ElementInstance> validateNotFound(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {
    if (elementInstance == null
        || elementInstance.getParentKey() > 0
        || elementInstance.isTerminating()) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND, PROCESS_NOT_FOUND_MESSAGE.formatted(command.getKey())));
    }
    return Either.right(elementInstance);
  }

  private Either<Rejection, ElementInstance> validateAuthorized(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {
    return cslCheck
        .checkAuthorizationAndTenant(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.SUSPEND_PROCESS_INSTANCE))
                        .resourceId(elementInstance.getValue().getBpmnProcessId())),
            elementInstance.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.SUSPEND_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION),
            elementInstance.getValue().getTenantId(),
            new Rejection(
                RejectionType.NOT_FOUND,
                PROCESS_NOT_FOUND_MESSAGE.formatted(
                    elementInstance.getValue().getProcessInstanceKey())))
        .map(ignored -> elementInstance);
  }

  private Either<Rejection, ElementInstance> validateSuspensionState(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {
    final var marker = suspensionState.getSuspensionState(command.getKey());
    if (marker != SuspensionState.State.SUSPENDED && marker != SuspensionState.State.RESUMING) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              PROCESS_NOT_SUSPENDED_MESSAGE.formatted(command.getKey())));
    }
    return Either.right(elementInstance);
  }

  private void resume(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {
    final ProcessInstanceRecord value = elementInstance.getValue();
    final boolean isRestart =
        suspensionState.getSuspensionState(command.getKey()) == SuspensionState.State.RESUMING;

    if (!isRestart) {
      LOG.debug("Resuming process instance '{}': was suspended, starting drain", command.getKey());
      // switch the marker to RESUMING before the first DRAIN so the buffered commands it writes
      // back are let through by the suspension gate instead of being buffered again
      stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.RESUMING, value);
    } else {
      LOG.debug(
          "Resuming process instance '{}': drain was already in progress, restarting it",
          command.getKey());
    }
    // restarting a RESUMING instance skips the event above (the marker is already there) and
    // just appends a fresh DRAIN, giving a drain halted by a since-fixed failure (see
    // BufferedCommandDrainProcessor) a way back in without a duplicate audit event
    commandWriter.appendFollowUpCommand(
        command.getKey(),
        BufferedCommandIntent.DRAIN,
        new BufferedCommandRecord()
            .setProcessInstanceKey(command.getKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setTenantId(value.getTenantId()));

    // the request is answered as soon as resuming has started (or restarted): draining the
    // buffer spans several command batches, and RESUMED is written only once it is empty
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ProcessInstanceIntent.RESUMING, value, command);
    if (!isRestart) {
      suspensionMetrics.startResumeDuration(command.getKey());
    }
  }

  /**
   * Enriches the command value with fields from the element instance to ensure rejection records
   * have the proper context for audit logs export.
   */
  private void enrichRejectionCommand(
      final TypedRecord<ProcessInstanceRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    command.getValue().setTenantId(processInstanceRecord.getTenantId());
    command.getValue().setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }
}
