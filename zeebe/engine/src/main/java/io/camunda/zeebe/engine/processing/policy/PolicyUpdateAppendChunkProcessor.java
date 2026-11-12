/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
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
 * is buffered, the processor starts applying the update with its first chunk. It applies the update
 * with the identifiers and chunk count that are buffered with the update, which is the same count
 * that decides whether every chunk is buffered.
 *
 * <p>A chunk that is already buffered makes the update invalid: the processor fails the update,
 * which removes all its chunks, and rejects the chunk. Otherwise the processor does not validate
 * the chunks yet: it assumes that the broker sends every chunk of an update with the update's
 * identifiers, and one update at a time. The processor checks no user authorization; see {@link
 * PolicyUpdateGuard}.
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
    final var rejection = PolicyUpdateGuard.checkInternal(command);
    if (rejection.isPresent()) {
      rejectionWriter.appendRejection(command, rejection.get().type(), rejection.get().reason());
      return;
    }

    final var chunk = command.getValue();
    if (policyUpdateState.hasChunk(chunk.getUpdateId(), chunk.getChunkIndex())) {
      failUpdate(command, chunk);
      return;
    }

    final boolean wasComplete = completeUpdate(chunk.getUpdateId()).isPresent();
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.CHUNK_APPENDED, chunk);
    if (wasComplete) {
      return;
    }

    completeUpdate(chunk.getUpdateId())
        .ifPresent(
            update ->
                commandWriter.appendFollowUpCommand(
                    key,
                    PolicyUpdateIntent.APPLY_CHUNK,
                    PolicyUpdateRecords.withoutPolicyData(update.getUpdate(), 0)));
  }

  private void failUpdate(
      final TypedRecord<PolicyUpdateRecord> command, final PolicyUpdateRecord chunk) {
    final var reason =
        "Expected chunk %d of policy update '%s' to be received once, but it was received again"
            .formatted(chunk.getChunkIndex(), chunk.getUpdateId());
    final var update =
        policyUpdateState.getUpdate(chunk.getUpdateId()).map(PersistedPolicyUpdate::getUpdate);
    final var failed =
        PolicyUpdateRecords.withoutPolicyData(update.orElse(chunk), chunk.getChunkIndex())
            .setErrorMessage(reason);
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), PolicyUpdateIntent.FAILED, failed);
    rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, reason);
  }

  /** The update, if every one of its chunks is buffered. */
  private Optional<PersistedPolicyUpdate> completeUpdate(final String updateId) {
    return policyUpdateState
        .getUpdate(updateId)
        .filter(PersistedPolicyUpdate::hasAllChunksBuffered);
  }
}
