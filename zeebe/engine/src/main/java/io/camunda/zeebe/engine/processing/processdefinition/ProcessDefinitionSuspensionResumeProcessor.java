/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Iterator;
import org.jspecify.annotations.NullMarked;

/**
 * Resumes the process definition versions a command resolves to: a single version by key or by BPMN
 * process ID and version, or all versions by BPMN process ID only. Writes a {@link
 * ProcessIntent#RESUMED} event for each suspended version, then one request event with an outcome
 * per resolved version, like resource deletion writes {@link ProcessIntent#DRAINING} per
 * definition.
 *
 * <p>When a version changes, the command is distributed to the other partitions in the {@link
 * DistributionQueue#DEPLOYMENT} queue, behind the deployments and suspensions of these versions.
 * The response doesn't wait for the other partitions, so they still treat the version as suspended
 * until they have applied the distributed command.
 */
@NullMarked
public final class ProcessDefinitionSuspensionResumeProcessor
    implements DistributedTypedRecordProcessor<ProcessDefinitionSuspensionRecord> {

  private final ProcessDefinitionSuspensionBehavior behavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public ProcessDefinitionSuspensionResumeProcessor(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final CommandDistributionBehavior commandDistributionBehavior) {
    behavior =
        new ProcessDefinitionSuspensionBehavior(
            processingState, writers, cslCheck, tenantCheck, commandDistributionBehavior, "resume");
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  @Override
  public void processNewCommand(final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var resolved = behavior.findVersions(command);
    if (resolved.isLeft()) {
      behavior.reject(command, resolved.getLeft());
      return;
    }

    final var value = command.getValue();
    final long key = keyGenerator.nextKey();
    final boolean hasStateChanges = resumeVersions(value, resolved.get());

    stateWriter.appendFollowUpEvent(key, ProcessDefinitionSuspensionIntent.RESUMED, value);
    if (hasStateChanges) {
      behavior.distribute(key, command);
    }
    responseWriter.writeAcceptedResponseOnCommand(
        key, ProcessDefinitionSuspensionIntent.RESUMED, value, command);
  }

  /** Resumes the versions the deployment partition resumed, as decided there (see ADR 0015). */
  @Override
  public void processDistributedCommand(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    final var versions =
        behavior.findVersionsWithOutcome(value, ProcessDefinitionSuspensionOutcome.RESUMED);
    while (versions.hasNext()) {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), ProcessIntent.RESUMED, versions.next().toProcessRecord());
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), ProcessDefinitionSuspensionIntent.RESUMED, value);
    behavior.acknowledge(command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command, final Throwable error) {
    final var processingError = behavior.rejectIfTooLarge(command, error);
    behavior.acknowledgeIfDistributedAndExpected(command, processingError);
    return processingError;
  }

  /** Returns whether any version changed. */
  private boolean resumeVersions(
      final ProcessDefinitionSuspensionRecord value, final Iterator<DeployedProcess> versions) {
    boolean hasStateChanges = false;
    while (versions.hasNext()) {
      final var process = versions.next();
      final var outcome = outcomeOf(process);
      value.addResolvedVersion(
          ProcessDefinitionSuspensionBehavior.resolvedVersion(process, outcome));
      if (outcome == ProcessDefinitionSuspensionOutcome.RESUMED) {
        stateWriter.appendFollowUpEvent(
            keyGenerator.nextKey(), ProcessIntent.RESUMED, process.toProcessRecord());
        hasStateChanges = true;
      }
    }
    return hasStateChanges;
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
