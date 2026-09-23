/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.managedscriptdefinition;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ManagedScriptDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class ManagedScriptDefinitionUpdateProcessor
    implements TypedRecordProcessor<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionState state;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final InstantSource clock;

  public ManagedScriptDefinitionUpdateProcessor(
      final MutableProcessingState processingState,
      final Writers writers,
      final InstantSource clock) {
    state = processingState.getManagedScriptDefinitionState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ManagedScriptDefinitionRecord> command) {
    final var request = command.getValue();
    final var current = state.getManagedScriptDefinition(command.getKey());
    final var rejection = validate(command.getKey(), request, current);
    if (rejection != null) {
      reject(command, rejection.type(), rejection.reason());
      return;
    }

    if (request.getOperationId().equals(current.getOperationId())) {
      responseWriter.writeAcceptedResponseOnCommand(
          command.getKey(), ManagedScriptDefinitionIntent.UPDATED, current, command);
      return;
    }

    final var updated = new ManagedScriptDefinitionRecord();
    updated.copyFrom(current);
    updated
        .setRevision(current.getRevision() + 1)
        .setStatus(request.getStatus())
        .setOperationId(request.getOperationId())
        .setLeaseDuration(-1L);
    applyStatusFields(request, updated);
    if (isTerminal(updated.getStatus())) {
      updated.setLeaseOwner("").setLeaseToken("").setLeaseExpiresAt(-1L);
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), ManagedScriptDefinitionIntent.UPDATED, updated);
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ManagedScriptDefinitionIntent.UPDATED, updated, command);
  }

  private static void applyStatusFields(
      final ManagedScriptDefinitionRecord request, final ManagedScriptDefinitionRecord updated) {
    switch (request.getStatus()) {
      case DEPLOYING ->
          updated
              .setProviderOperationId(request.getProviderOperationId())
              .setFailureCode("")
              .setFailureMessage("")
              .setRetryable(false);
      case READY ->
          updated
              .setProviderDeploymentId(request.getProviderDeploymentId())
              .setFailureCode("")
              .setFailureMessage("")
              .setRetryable(false);
      case FAILED ->
          updated
              .setFailureCode(request.getFailureCode())
              .setFailureMessage(request.getFailureMessage())
              .setRetryable(request.isRetryable());
      default -> throw new IllegalStateException("Unsupported managed script transition");
    }
  }

  private Rejection validate(
      final long key,
      final ManagedScriptDefinitionRecord request,
      final ManagedScriptDefinitionRecord current) {
    if (current == null) {
      return new Rejection(
          RejectionType.NOT_FOUND,
          "Expected to update managed script definition '%d', but it was not found".formatted(key));
    }
    if (request.getOperationId().isBlank()) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected managed script transition operation ID to be present");
    }
    if (request.getOperationId().equals(current.getOperationId())) {
      return null;
    }
    if (request.getRevision() != current.getRevision()) {
      return new Rejection(
          RejectionType.INVALID_STATE,
          "Expected managed script definition '%d' to have revision '%d', but it has revision '%d'"
              .formatted(key, request.getRevision(), current.getRevision()));
    }
    if (!request.getLeaseToken().equals(current.getLeaseToken())
        || current.getLeaseExpiresAt() <= clock.millis()) {
      return new Rejection(
          RejectionType.INVALID_STATE,
          "Expected an active lease for managed script definition '%d'".formatted(key));
    }
    if (!isAllowed(current.getStatus(), request.getStatus())) {
      return new Rejection(
          RejectionType.INVALID_STATE,
          "Expected a valid transition for managed script definition '%d', but cannot transition from '%s' to '%s'"
              .formatted(key, current.getStatus(), request.getStatus()));
    }
    if (request.getStatus() == ManagedScriptDefinitionStatus.DEPLOYING
        && request.getProviderOperationId().isBlank()) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected provider operation ID when transitioning managed script definition to DEPLOYING");
    }
    if (request.getStatus() == ManagedScriptDefinitionStatus.READY
        && request.getProviderDeploymentId().isBlank()) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected provider deployment ID when transitioning managed script definition to READY");
    }
    if (request.getStatus() == ManagedScriptDefinitionStatus.FAILED
        && request.getFailureCode().isBlank()) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected failure code when transitioning managed script definition to FAILED");
    }
    return null;
  }

  private static boolean isAllowed(
      final ManagedScriptDefinitionStatus current, final ManagedScriptDefinitionStatus requested) {
    return switch (requested) {
      case DEPLOYING ->
          current == ManagedScriptDefinitionStatus.PENDING
              || current == ManagedScriptDefinitionStatus.BUILDING
              || current == ManagedScriptDefinitionStatus.DEPLOYING;
      case READY ->
          current == ManagedScriptDefinitionStatus.PENDING
              || current == ManagedScriptDefinitionStatus.BUILDING
              || current == ManagedScriptDefinitionStatus.DEPLOYING;
      case FAILED ->
          current == ManagedScriptDefinitionStatus.PENDING
              || current == ManagedScriptDefinitionStatus.BUILDING
              || current == ManagedScriptDefinitionStatus.DEPLOYING;
      default -> false;
    };
  }

  private static boolean isTerminal(final ManagedScriptDefinitionStatus status) {
    return status == ManagedScriptDefinitionStatus.READY
        || status == ManagedScriptDefinitionStatus.FAILED;
  }

  private void reject(
      final TypedRecord<ManagedScriptDefinitionRecord> command,
      final RejectionType type,
      final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }

  private record Rejection(RejectionType type, String reason) {}
}
