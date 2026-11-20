/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Suspends the process definition versions a command resolves to: a single version by key or by
 * BPMN process ID and version, or all versions by BPMN process ID only. Writes a {@link
 * ProcessIntent#SUSPENDED} event for each version that wasn't suspended yet, then one request event
 * with an outcome per resolved version, like resource deletion writes {@link
 * ProcessIntent#DRAINING} per definition.
 */
@NullMarked
public final class ProcessDefinitionSuspensionSuspendProcessor
    implements TypedRecordProcessor<ProcessDefinitionSuspensionRecord> {

  private static final String VERB = "suspend";

  private final ProcessDefinitionSuspensionBehavior behavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final CslAuthorizationCheck cslCheck;

  public ProcessDefinitionSuspensionSuspendProcessor(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck) {
    behavior = new ProcessDefinitionSuspensionBehavior(processingState, writers, tenantCheck);
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var resolved = resolveVersions(command);
    if (resolved.isLeft()) {
      behavior.reject(command, resolved.getLeft());
      return;
    }

    final var value = command.getValue();
    final long key = keyGenerator.nextKey();
    final var versions = resolved.get();
    while (versions.hasNext()) {
      final var process = versions.next();
      final var outcome = outcomeOf(process);
      value.addResolvedVersion(
          ProcessDefinitionSuspensionBehavior.resolvedVersion(process, outcome));
      if (outcome == ProcessDefinitionSuspensionOutcome.SUSPENDED) {
        stateWriter.appendFollowUpEvent(
            keyGenerator.nextKey(), ProcessIntent.SUSPENDED, process.toProcessRecord());
      }
    }

    stateWriter.appendFollowUpEvent(key, ProcessDefinitionSuspensionIntent.SUSPENDED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        key, ProcessDefinitionSuspensionIntent.SUSPENDED, value, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command, final Throwable error) {
    return behavior.rejectIfTooLarge(command, error, VERB);
  }

  private Either<Rejection, Iterator<DeployedProcess>> resolveVersions(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    return behavior
        .validateIdentifiers(command.getValue(), VERB)
        .flatMap(
            value ->
                ProcessDefinitionSuspensionBehavior.isByKey(value)
                    ? resolveByKey(command)
                    : resolveById(command));
  }

  /**
   * The BPMN process ID to authorize against is only known after the lookup, so every failure is a
   * {@code NOT_FOUND}.
   */
  private Either<Rejection, Iterator<DeployedProcess>> resolveByKey(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    final var notFound = ProcessDefinitionSuspensionBehavior.notFoundByKey(value, VERB);
    return behavior
        .findByKey(command, VERB)
        .flatMap(
            process ->
                cslCheck.checkAuthorizationAndTenant(
                    command,
                    ProcessDefinitionSuspensionBehavior.requiredAuthorization(
                        bufferAsString(process.getBpmnProcessId())),
                    process,
                    notFound,
                    rejection -> notFound,
                    process.getTenantId(),
                    notFound))
        .flatMap(process -> behavior.rejectIfDraining(process, VERB))
        .map(process -> List.of(process).iterator());
  }

  /** The permission is checked before the lookup, so a caller without it learns nothing. */
  private Either<Rejection, Iterator<DeployedProcess>> resolveById(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    return cslCheck
        .checkAuthorizationAndTenant(
            command,
            ProcessDefinitionSuspensionBehavior.requiredAuthorization(value.getBpmnProcessId()),
            value,
            ProcessDefinitionSuspensionBehavior.forbidden(),
            value.getTenantId(),
            ProcessDefinitionSuspensionBehavior.notFoundById(value, VERB))
        .flatMap(authorized -> behavior.findById(value, VERB));
  }

  private ProcessDefinitionSuspensionOutcome outcomeOf(final DeployedProcess process) {
    if (process.isDraining()) {
      return ProcessDefinitionSuspensionOutcome.DRAINING;
    }
    return behavior.isSuspended(process)
        ? ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED
        : ProcessDefinitionSuspensionOutcome.SUSPENDED;
  }
}
