/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.resource.StartEventSubscriptions;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared flow of suspending and resuming a process definition version: look up the version by key,
 * validate, write the event (whose applier flips the marker), hand over start event subscriptions
 * and distribute the command to the other partitions.
 */
@NullMarked
abstract class AbstractProcessDefinitionSuspensionProcessor
    implements DistributedTypedRecordProcessor<ProcessDefinitionSuspensionRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final ProcessState processState;
  private final TenantState tenantState;
  private final CslTenantCheck tenantCheck;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final String verb;
  private final ProcessDefinitionSuspensionIntent eventIntent;
  private final StartEventSubscriptions startEventSubscriptions;

  AbstractProcessDefinitionSuspensionProcessor(
      final String verb,
      final ProcessDefinitionSuspensionIntent eventIntent,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CslTenantCheck tenantCheck,
      final CommandDistributionBehavior commandDistributionBehavior,
      final StartEventSubscriptions startEventSubscriptions) {
    this.startEventSubscriptions = startEventSubscriptions;
    this.verb = verb;
    this.eventIntent = eventIntent;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    processState = processingState.getProcessState();
    tenantState = processingState.getTenantState();
    this.tenantCheck = tenantCheck;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  /**
   * @return a rejection reason if the version's suspension marker does not allow this operation,
   *     otherwise null
   */
  abstract @Nullable String validateMarker(long processDefinitionKey, boolean suspended);

  @Override
  public void processNewCommand(final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    // TODO(POC): check SUSPEND_PROCESS_DEFINITION permission
    final var value = command.getValue();
    final var process = findProcess(command);
    if (process == null) {
      reject(
          command,
          RejectionType.NOT_FOUND,
          "Expected to %s process definition with key '%d', but no such process definition was found"
              .formatted(verb, value.getProcessDefinitionKey()));
      return;
    }
    final var rejection = validate(process);
    if (rejection != null) {
      reject(command, RejectionType.INVALID_STATE, rejection);
      return;
    }

    final long eventKey = keyGenerator.nextKey();
    apply(process, value, eventKey, true);

    responseWriter.writeAcceptedResponseOnCommand(eventKey, eventIntent, value, command);
    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.DEPLOYMENT)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    final var process =
        processState.getProcessByKeyAndTenant(value.getProcessDefinitionKey(), value.getTenantId());
    // A copy that no longer applies (e.g. the version was deleted in the meantime) is dropped
    // silently; the caller was already answered on the origin partition.
    if (process != null && validate(process) == null) {
      apply(process, value, command.getKey(), false);
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void apply(
      final DeployedProcess process,
      final ProcessDefinitionSuspensionRecord value,
      final long eventKey,
      final boolean subscribeToTimers) {
    value
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setTenantId(process.getTenantId());

    final var ownerBefore =
        processState.getLatestActiveProcessVersionByProcessId(
            process.getBpmnProcessId(), process.getTenantId());
    stateWriter.appendFollowUpEvent(eventKey, eventIntent, value);
    final var ownerAfter =
        processState.getLatestActiveProcessVersionByProcessId(
            process.getBpmnProcessId(), process.getTenantId());
    startEventSubscriptions.moveStartEventSubscriptions(ownerBefore, ownerAfter, subscribeToTimers);
  }

  private @Nullable String validate(final DeployedProcess process) {
    final long key = process.getKey();
    if (process.getState() != PersistedProcessState.ACTIVE) {
      return "Expected to %s process definition with key '%d', but it is being deleted"
          .formatted(verb, key);
    }
    return validateMarker(key, processState.isProcessDefinitionSuspended(key));
  }

  private void reject(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command,
      final RejectionType type,
      final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }

  private @Nullable DeployedProcess findProcess(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final long key = command.getValue().getProcessDefinitionKey();
    final var authorized = tenantCheck.resolveAuthorizedTenants(command.getAuthInfo());
    if (!authorized.wildcard()) {
      for (final String tenantId : authorized.tenantIds()) {
        final var process = processState.getProcessByKeyAndTenant(key, tenantId);
        if (process != null) {
          return process;
        }
      }
      return null;
    }
    final var defaultTenantProcess =
        processState.getProcessByKeyAndTenant(key, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    if (defaultTenantProcess != null) {
      return defaultTenantProcess;
    }
    final var found = new AtomicReference<@Nullable DeployedProcess>();
    tenantState.forEachTenant(
        tenantId -> {
          found.set(processState.getProcessByKeyAndTenant(key, tenantId));
          return found.get() == null;
        });
    return found.get();
  }
}
