/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.NO_KEY;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Applies one buffered chunk of a policy update, once all its chunks are buffered. The chunk is
 * turned into identity commands, which the identity processors apply and distribute to the other
 * partitions. Each identity processor validates its own command. The commands carry the update's
 * key as reference, so if one cannot be applied, for example adding a member to a missing role, the
 * update fails and its remaining commands are rejected; see {@link PolicyUpdateBehavior}. The
 * commands applied before the failure are not undone.
 *
 * <p>The processor then writes the command for the next chunk. Its identity commands are written
 * after those of this chunk, so the chunks are applied in order. After the last chunk, it instead
 * writes the command that commits the update's policy version.
 *
 * <p>The processor checks no user authorization; see {@link PolicyUpdateGuard}.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateApplyChunkProcessor
    implements TypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateState policyUpdateState;
  private final PolicyUpdateCommandMapper commandMapper;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateApplyChunkProcessor(
      final PolicyUpdateState policyUpdateState,
      final PolicyUpdateCommandMapper commandMapper,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.commandMapper = commandMapper;
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

    final var update = command.getValue();
    final var openUpdate =
        policyUpdateState
            .getOpenUpdate()
            .filter(open -> open.getUpdate().getUpdateId().equals(update.getUpdateId()));
    if (openUpdate.isEmpty()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          "Expected policy update '%s' to be open to apply chunk %d, but it failed or no longer exists"
              .formatted(update.getUpdateId(), update.getChunkIndex()));
      return;
    }

    final var chunk = policyUpdateState.getChunk(update.getUpdateId(), update.getChunkIndex());
    if (chunk.isEmpty()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected chunk %d of policy update '%s' to be buffered, but it is not"
              .formatted(update.getChunkIndex(), update.getUpdateId()));
      return;
    }

    final var commands = commandMapper.toCommands(chunk.get());
    if (commands.isLeft()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected every entry of policy update '%s' of policy '%s' to name its entity type, but: %s"
              .formatted(
                  update.getUpdateId(),
                  update.getPolicyId(),
                  String.join("; ", commands.getLeft())));
      return;
    }
    final var identityMetadata = PolicyUpdateBehavior.metadataFor(openUpdate.get());
    commands
        .get()
        .forEach(
            identityCommand ->
                commandWriter.appendFollowUpCommand(
                    NO_KEY, identityCommand.intent(), identityCommand.value(), identityMetadata));

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(),
        PolicyUpdateIntent.CHUNK_APPLIED,
        PolicyUpdateRecords.withoutPolicyData(update, update.getChunkIndex()));

    final int nextChunkIndex = update.getChunkIndex() + 1;
    if (nextChunkIndex < update.getChunkCount()) {
      commandWriter.appendFollowUpCommand(
          command.getKey(),
          PolicyUpdateIntent.APPLY_CHUNK,
          PolicyUpdateRecords.withoutPolicyData(update, nextChunkIndex));
    } else {
      // the last chunk completes the update, which is then committed
      commandWriter.appendFollowUpCommand(
          NO_KEY,
          PolicyUpdateIntent.COMMIT_VERSION,
          PolicyUpdateRecords.withoutPolicyData(update, update.getChunkIndex()));
    }
  }

  /**
   * The identity commands are processed in their own batches, not in the batch of this command. If
   * an identity processor throws, only its own command is rolled back and handled by its own error
   * handling, which fails the update. In this batch, the whole chunk would be rolled back and the
   * error handled for this command, leaving the update open without a next command.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }
}
