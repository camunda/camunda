/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Buffers a chunk of a policy update. Chunks may arrive in any order. Once the last missing chunk
 * is buffered, the processor starts applying the update with its first chunk.
 *
 * <p>There is at most one open update: a chunk of another update is rejected until the open one is
 * applied. The processor checks no user authorization; see {@link PolicyUpdateOrigin}.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateAppendChunkProcessor
    implements TypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateState policyUpdateState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateAppendChunkProcessor(
      final PolicyUpdateState policyUpdateState,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyUpdateRecord> command) {
    final var chunk = command.getValue();
    final var openUpdate = policyUpdateState.getOpenUpdate();
    final var rejection =
        PolicyUpdateOrigin.checkInternal(command)
            .or(() -> rejectInvalidChunk(chunk))
            .or(() -> rejectUnlessChunkFits(chunk, openUpdate));
    if (rejection.isPresent()) {
      rejectionWriter.appendRejection(command, rejection.get().type(), rejection.get().reason());
      return;
    }

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.CHUNK_APPENDED, chunk);

    final int bufferedChunkCount =
        openUpdate.map(PersistedPolicyUpdate::getBufferedChunkCount).orElse(0) + 1;
    if (bufferedChunkCount == chunk.getChunkCount()) {
      commandWriter.appendFollowUpCommand(
          key, PolicyUpdateIntent.APPLY_CHUNK, PolicyUpdateRecords.withoutPolicyData(chunk, 0));
    }
  }

  private static Optional<Rejection> rejectInvalidChunk(final PolicyUpdateRecord chunk) {
    if (chunk.getUpdateId().isEmpty()) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected a chunk of policy '%s' to name its update, but the update ID is empty"
                  .formatted(chunk.getPolicyId())));
    }
    if (chunk.getChunkCount() < 1
        || chunk.getChunkIndex() < 0
        || chunk.getChunkIndex() >= chunk.getChunkCount()) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected chunk %d of policy update '%s' to be one of its %d chunks, but it is not"
                  .formatted(chunk.getChunkIndex(), chunk.getUpdateId(), chunk.getChunkCount())));
    }
    final var unmappable = PolicyUpdateCommandMapper.findUnmappableEntries(chunk);
    if (!unmappable.isEmpty()) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected every entry of policy update '%s' of policy '%s' to name its entity type, but: %s"
                  .formatted(
                      chunk.getUpdateId(), chunk.getPolicyId(), String.join("; ", unmappable))));
    }
    return Optional.empty();
  }

  private Optional<Rejection> rejectUnlessChunkFits(
      final PolicyUpdateRecord chunk, final Optional<PersistedPolicyUpdate> openUpdate) {
    if (openUpdate.isEmpty()) {
      return Optional.empty();
    }
    final var update = openUpdate.get().getUpdate();
    if (!update.getUpdateId().equals(chunk.getUpdateId())) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "Expected no other policy update to be open when appending a chunk of '%s', but '%s' is still open"
                  .formatted(chunk.getUpdateId(), update.getUpdateId())));
    }
    if (!hasSameIdentifiers(update, chunk)) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected chunk %d of policy update '%s' to match the chunks before (policy '%s', version %d from %d, seqNo %d, %d chunks), but it has policy '%s', version %d from %d, seqNo %d, %d chunks"
                  .formatted(
                      chunk.getChunkIndex(),
                      chunk.getUpdateId(),
                      update.getPolicyId(),
                      update.getPolicyVersion(),
                      update.getBasePolicyVersion(),
                      update.getSeqNo(),
                      update.getChunkCount(),
                      chunk.getPolicyId(),
                      chunk.getPolicyVersion(),
                      chunk.getBasePolicyVersion(),
                      chunk.getSeqNo(),
                      chunk.getChunkCount())));
    }
    if (policyUpdateState.hasChunk(chunk.getUpdateId(), chunk.getChunkIndex())) {
      return Optional.of(
          new Rejection(
              RejectionType.ALREADY_EXISTS,
              "Expected chunk %d of policy update '%s' to be new, but it is already buffered"
                  .formatted(chunk.getChunkIndex(), chunk.getUpdateId())));
    }
    return Optional.empty();
  }

  private static boolean hasSameIdentifiers(
      final PolicyUpdateRecord update, final PolicyUpdateRecord chunk) {
    return update.getPolicyId().equals(chunk.getPolicyId())
        && update.getPolicyVersion() == chunk.getPolicyVersion()
        && update.getBasePolicyVersion() == chunk.getBasePolicyVersion()
        && update.getSeqNo() == chunk.getSeqNo()
        && update.getChunkCount() == chunk.getChunkCount();
  }
}
