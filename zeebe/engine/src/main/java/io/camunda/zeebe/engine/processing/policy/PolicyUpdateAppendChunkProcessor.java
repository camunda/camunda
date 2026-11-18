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
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/**
 * Buffers a chunk of a policy update. The broker writes the chunks of an update one after another,
 * in index order, and the log keeps that order, so each chunk must be the next one of its update.
 * Once the last chunk is buffered, the processor starts applying the update with its first chunk.
 * It applies the update with the identifiers and chunk count that are buffered with the update,
 * which is the same count that decides whether every chunk is buffered.
 *
 * <p>Before the last chunk is buffered, the processor validates the whole update, all its chunks
 * together, with {@link PolicyUpdateValidator}. If the update is invalid, it fails the update and
 * rejects the chunk, so nothing of the update is applied.
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

  /**
   * The reasons of an invalid update that the failure names. The failure event and the rejection
   * both carry them, and must fit into one batch together with the rejected chunk, however many
   * entries of the update are invalid.
   */
  static final int MAX_REPORTED_ERRORS = 10;

  private final PolicyUpdateState policyUpdateState;
  private final PolicyUpdateValidator validator;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateAppendChunkProcessor(
      final PolicyUpdateState policyUpdateState,
      final PolicyUpdateValidator validator,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.validator = validator;
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

    // after the checks, the chunk is the next chunk of the open update, or opens it
    final var updateOfChunk = openUpdate.map(PersistedPolicyUpdate::getUpdate).orElse(chunk);
    final int bufferedChunkCount =
        openUpdate.map(PersistedPolicyUpdate::getBufferedChunkCount).orElse(0);
    if (bufferedChunkCount + 1 == updateOfChunk.getChunkCount()) {
      final var errors = validator.validate(chunksOfUpdate(updateOfChunk, chunk));
      if (!errors.isEmpty()) {
        failUpdate(
            command,
            updateOfChunk,
            RejectionType.INVALID_ARGUMENT,
            "Expected policy update '%s' of policy '%s' to be applicable, but: %s"
                .formatted(
                    updateOfChunk.getUpdateId(), updateOfChunk.getPolicyId(), summarize(errors)));
        return;
      }
    }

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.CHUNK_APPENDED, chunk);
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

    // a lower index is a chunk received again, a higher one skips a chunk; as the next chunk is
    // below the chunk count, this also keeps every index in range
    final int nextChunkIndex = persistedUpdate.getBufferedChunkCount();
    if (chunk.getChunkIndex() != nextChunkIndex) {
      failUpdate(
          command,
          openUpdate,
          RejectionType.INVALID_ARGUMENT,
          "Expected chunk %d of policy update '%s' to be received next, but received chunk %d"
              .formatted(nextChunkIndex, chunk.getUpdateId(), chunk.getChunkIndex()));
      return false;
    }
    return true;
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

  private static String summarize(final List<String> errors) {
    if (errors.size() <= MAX_REPORTED_ERRORS) {
      return String.join("; ", errors);
    }
    return "%s; and %d more"
        .formatted(
            String.join("; ", errors.subList(0, MAX_REPORTED_ERRORS)),
            errors.size() - MAX_REPORTED_ERRORS);
  }

  /** All chunks of the update, in their order: the buffered ones and the given new one. */
  private List<PolicyUpdateRecord> chunksOfUpdate(
      final PolicyUpdateRecord update, final PolicyUpdateRecord newChunk) {
    return IntStream.range(0, update.getChunkCount())
        .mapToObj(
            index ->
                index == newChunk.getChunkIndex()
                    ? newChunk
                    : policyUpdateState.getChunk(update.getUpdateId(), index).orElseThrow())
        .toList();
  }

  /** The open update, if every one of its chunks is buffered. */
  private Optional<PersistedPolicyUpdate> completeUpdate() {
    return policyUpdateState.getOpenUpdate().filter(PersistedPolicyUpdate::hasAllChunksBuffered);
  }
}
