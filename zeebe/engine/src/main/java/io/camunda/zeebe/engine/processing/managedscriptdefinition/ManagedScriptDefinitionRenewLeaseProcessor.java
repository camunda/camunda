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
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class ManagedScriptDefinitionRenewLeaseProcessor
    implements TypedRecordProcessor<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionState state;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final InstantSource clock;

  public ManagedScriptDefinitionRenewLeaseProcessor(
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

    final var renewed = new ManagedScriptDefinitionRecord();
    renewed.copyFrom(current);
    renewed.setLeaseExpiresAt(clock.millis() + request.getLeaseDuration()).setLeaseDuration(-1L);
    stateWriter.appendFollowUpEvent(
        command.getKey(), ManagedScriptDefinitionIntent.LEASE_RENEWED, renewed);
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ManagedScriptDefinitionIntent.LEASE_RENEWED, renewed, command);
  }

  private Rejection validate(
      final long key,
      final ManagedScriptDefinitionRecord request,
      final ManagedScriptDefinitionRecord current) {
    if (current == null) {
      return new Rejection(
          RejectionType.NOT_FOUND,
          "Expected to renew lease for managed script definition '%d', but it was not found"
              .formatted(key));
    }
    if (request.getLeaseDuration() <= 0) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected managed script lease duration to be greater than zero");
    }
    if (request.getRevision() != current.getRevision()) {
      return staleRevision(key, request, current);
    }
    if (!request.getLeaseToken().equals(current.getLeaseToken())
        || current.getLeaseExpiresAt() <= clock.millis()) {
      return staleLease(key);
    }
    return null;
  }

  static Rejection staleRevision(
      final long key,
      final ManagedScriptDefinitionRecord request,
      final ManagedScriptDefinitionRecord current) {
    return new Rejection(
        RejectionType.INVALID_STATE,
        "Expected managed script definition '%d' to have revision '%d', but it has revision '%d'"
            .formatted(key, request.getRevision(), current.getRevision()));
  }

  static Rejection staleLease(final long key) {
    return new Rejection(
        RejectionType.INVALID_STATE,
        "Expected an active lease for managed script definition '%d'".formatted(key));
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
