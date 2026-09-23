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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

@ExcludeAuthorizationCheck
public final class ManagedScriptDefinitionActivateProcessor
    implements TypedRecordProcessor<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionState state;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;
  private final int partitionId;

  public ManagedScriptDefinitionActivateProcessor(
      final MutableProcessingState processingState,
      final Writers writers,
      final InstantSource clock,
      final int partitionId) {
    state = processingState.getManagedScriptDefinitionState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    keyGenerator = processingState.getKeyGenerator();
    this.clock = clock;
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<ManagedScriptDefinitionRecord> command) {
    final var request = command.getValue();
    final var validationError = validate(request);
    if (validationError != null) {
      reject(command, validationError);
      return;
    }

    final long now = clock.millis();
    final List<Long> keys = new ArrayList<>();
    state.forEachManagedScriptDefinitionKey(keys::add);
    final var definition =
        keys.stream()
            .filter(key -> Protocol.decodePartitionId(key) == partitionId)
            .map(state::getManagedScriptDefinition)
            .filter(candidate -> isEligible(candidate, request, now))
            .findFirst()
            .map(candidate -> lease(candidate, request, now))
            .orElseGet(ManagedScriptDefinitionRecord::new);

    if (definition.getManagedScriptDefinitionKey() < 0) {
      responseWriter.writeAcceptedResponseOnCommand(
          -1L, ManagedScriptDefinitionIntent.LEASED, definition, command);
      return;
    }

    stateWriter.appendFollowUpEvent(
        definition.getManagedScriptDefinitionKey(),
        ManagedScriptDefinitionIntent.LEASED,
        definition);
    responseWriter.writeAcceptedResponseOnCommand(
        definition.getManagedScriptDefinitionKey(),
        ManagedScriptDefinitionIntent.LEASED,
        definition,
        command);
  }

  private ManagedScriptDefinitionRecord lease(
      final ManagedScriptDefinitionRecord candidate,
      final ManagedScriptDefinitionRecord request,
      final long now) {
    final var leased = new ManagedScriptDefinitionRecord();
    leased.copyFrom(candidate);
    return leased
        .setRevision(candidate.getRevision() + 1)
        .setProvider(request.getProvider())
        .setLeaseOwner(request.getLeaseOwner())
        .setLeaseToken(Long.toUnsignedString(keyGenerator.nextKey()))
        .setLeaseExpiresAt(now + request.getLeaseDuration())
        .setLeaseDuration(-1L);
  }

  private static boolean isEligible(
      final ManagedScriptDefinitionRecord candidate,
      final ManagedScriptDefinitionRecord request,
      final long now) {
    if (!candidate.getTenantId().equals(request.getTenantId())
        || (!candidate.getProvider().isEmpty()
            && !candidate.getProvider().equals(request.getProvider()))
        || candidate.getLeaseExpiresAt() > now) {
      return false;
    }
    return candidate.getStatus() == ManagedScriptDefinitionStatus.PENDING
        || candidate.getStatus() == ManagedScriptDefinitionStatus.BUILDING
        || candidate.getStatus() == ManagedScriptDefinitionStatus.DEPLOYING;
  }

  private static String validate(final ManagedScriptDefinitionRecord request) {
    if (request.getProvider().isBlank()) {
      return "Expected managed script activation provider to be present";
    }
    if (request.getLeaseOwner().isBlank()) {
      return "Expected managed script activation worker to be present";
    }
    if (request.getLeaseDuration() <= 0) {
      return "Expected managed script activation lease duration to be greater than zero";
    }
    if (request.getTenantId().isBlank()) {
      return "Expected managed script activation tenant ID to be present";
    }
    return null;
  }

  private void reject(
      final TypedRecord<ManagedScriptDefinitionRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
    responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_ARGUMENT, reason);
  }
}
