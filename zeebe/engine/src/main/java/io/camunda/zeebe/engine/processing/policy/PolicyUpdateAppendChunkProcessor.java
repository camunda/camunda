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
 * Buffers a chunk of a policy update. An update starts with its first chunk; the other chunks may
 * arrive in any order. Once the last missing chunk is buffered, the processor starts applying the
 * update with its first chunk. It applies the update with the identifiers and chunk count that are
 * buffered with the update, which is the same count that decides whether every chunk is buffered.
 *
 * <p>Only one update is open at a time. The processor rejects a chunk without changing the open
 * update if it cannot belong to it.
 *
 * <p>Once every chunk is buffered, the update is being applied: its identity commands may already
 * be written, and nothing can undo them. A chunk of it is then only rejected, and the update never
 * fails, whatever is wrong with the chunk.
 *
 * <p>The processor checks no user authorization; see {@link PolicyUpdateGuard}.
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
    final var openUpdate = policyUpdateState.getOpenUpdate();
    if (openUpdate.isEmpty()) {
      if (!isChunkIndexInRange(chunk)) {
        rejectionWriter.appendRejection(
            command, RejectionType.INVALID_ARGUMENT, chunkIndexOutOfRange(chunk));
        return;
      }
      if (chunk.getChunkIndex() != 0) {
        rejectionWriter.appendRejection(
            command,
            RejectionType.INVALID_STATE,
            "Expected policy update '%s' to start with chunk 0, but chunk %d was received first"
                .formatted(chunk.getUpdateId(), chunk.getChunkIndex()));
        return;
      }
    } else if (!rejectOrFailForOpenUpdate(command, openUpdate.get())) {
      return;
    }

    final boolean wasComplete = completeUpdate().isPresent();
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.CHUNK_APPENDED, chunk);
    if (wasComplete) {
      return;
    }

    completeUpdate()
        .ifPresent(
            update ->
                commandWriter.appendFollowUpCommand(
                    key,
                    PolicyUpdateIntent.APPLY_CHUNK,
                    PolicyUpdateRecords.withoutPolicyData(update.getUpdate(), 0)));
  }

  /**
   * Checks the chunk against the open update. Returns {@code true} if the chunk may be buffered;
   * otherwise it has rejected the chunk, and failed the update if the chunk belongs to it and the
   * update is not being applied yet.
   */
  private boolean rejectOrFailForOpenUpdate(
      final TypedRecord<PolicyUpdateRecord> command, final PersistedPolicyUpdate persistedUpdate) {
    final var chunk = command.getValue();
    final var openUpdate = persistedUpdate.getUpdate();
    if (!openUpdate.getUpdateId().equals(chunk.getUpdateId())) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          ("Expected no other policy update to be open when chunk %d of policy update '%s' is"
                  + " received, but policy update '%s' is open")
              .formatted(chunk.getChunkIndex(), chunk.getUpdateId(), openUpdate.getUpdateId()));
      return false;
    }

    if (persistedUpdate.isApplying()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          ("Expected policy update '%s' to still receive chunks when chunk %d is received, but it"
                  + " is already being applied")
              .formatted(chunk.getUpdateId(), chunk.getChunkIndex()));
      return false;
    }

    final var differences = PolicyUpdateRecords.differingIdentifiers(openUpdate, chunk);
    if (!differences.isEmpty()) {
      failUpdate(
          command,
          openUpdate,
          RejectionType.INVALID_ARGUMENT,
          "Expected chunk %d of policy update '%s' to have the identifiers of the update, but it has %s"
              .formatted(
                  chunk.getChunkIndex(), chunk.getUpdateId(), String.join(", ", differences)));
      return false;
    }

    if (!isChunkIndexInRange(chunk)) {
      failUpdate(command, openUpdate, RejectionType.INVALID_ARGUMENT, chunkIndexOutOfRange(chunk));
      return false;
    }

    if (policyUpdateState.hasChunk(chunk.getUpdateId(), chunk.getChunkIndex())) {
      failUpdate(
          command,
          openUpdate,
          RejectionType.ALREADY_EXISTS,
          "Expected chunk %d of policy update '%s' to be received once, but it was received again"
              .formatted(chunk.getChunkIndex(), chunk.getUpdateId()));
      return false;
    }
    return true;
  }

  /**
   * Whether the index is one of the chunk's update. Together with the check for chunks that are
   * received twice, this makes sure that an update whose buffered chunk count equals its chunk
   * count has every one of its chunks buffered.
   */
  private static boolean isChunkIndexInRange(final PolicyUpdateRecord chunk) {
    return chunk.getChunkIndex() >= 0 && chunk.getChunkIndex() < chunk.getChunkCount();
  }

  private static String chunkIndexOutOfRange(final PolicyUpdateRecord chunk) {
    return ("Expected the chunk index of policy update '%s' to be at least 0 and less than the"
            + " chunk count %d, but it is %d")
        .formatted(chunk.getUpdateId(), chunk.getChunkCount(), chunk.getChunkIndex());
  }

  private void failUpdate(
      final TypedRecord<PolicyUpdateRecord> command,
      final PolicyUpdateRecord openUpdate,
      final RejectionType rejectionType,
      final String reason) {
    final var failed =
        PolicyUpdateRecords.withoutPolicyData(openUpdate, command.getValue().getChunkIndex())
            .setErrorMessage(reason);
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), PolicyUpdateIntent.FAILED, failed);
    rejectionWriter.appendRejection(command, rejectionType, reason);
  }

  /**
   * The open update, if every one of its chunks is buffered. After the checks, the chunk belongs to
   * the open update, or opens it.
   */
  private Optional<PersistedPolicyUpdate> completeUpdate() {
    return policyUpdateState.getOpenUpdate().filter(PersistedPolicyUpdate::hasAllChunksBuffered);
  }
}
