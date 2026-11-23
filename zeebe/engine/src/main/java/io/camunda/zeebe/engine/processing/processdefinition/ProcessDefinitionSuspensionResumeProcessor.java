/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

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
import org.jspecify.annotations.NullMarked;

/**
 * Resumes the process definition versions a command resolves to: a single version by key or by BPMN
 * process ID and version, or all versions by BPMN process ID only. Writes a {@link
 * ProcessIntent#RESUMED} event for each suspended version, then one request event with an outcome
 * per resolved version, like resource deletion writes {@link ProcessIntent#DRAINING} per
 * definition.
 */
@NullMarked
public final class ProcessDefinitionSuspensionResumeProcessor
    implements TypedRecordProcessor<ProcessDefinitionSuspensionRecord> {

  private final ProcessDefinitionSuspensionBehavior behavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public ProcessDefinitionSuspensionResumeProcessor(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck) {
    behavior =
        new ProcessDefinitionSuspensionBehavior(
            processingState, writers, cslCheck, tenantCheck, "resume");
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var resolved = behavior.findVersions(command);
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
      if (outcome == ProcessDefinitionSuspensionOutcome.RESUMED) {
        stateWriter.appendFollowUpEvent(
            keyGenerator.nextKey(), ProcessIntent.RESUMED, process.toProcessRecord());
      }
    }

    stateWriter.appendFollowUpEvent(key, ProcessDefinitionSuspensionIntent.RESUMED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        key, ProcessDefinitionSuspensionIntent.RESUMED, value, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command, final Throwable error) {
    return behavior.rejectIfTooLarge(command, error);
  }

  private ProcessDefinitionSuspensionOutcome outcomeOf(final DeployedProcess process) {
    if (process.isDraining()) {
      return ProcessDefinitionSuspensionOutcome.DRAINING;
    }
    return behavior.isSuspended(process)
        ? ProcessDefinitionSuspensionOutcome.RESUMED
        : ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED;
  }
}
