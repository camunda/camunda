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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Applies a policy update that consists of a single chunk. The chunk is turned into identity
 * commands, which the identity processors apply and distribute to the other partitions. Each
 * identity processor validates its own command, so an entry that cannot be applied, for example a
 * delete of a missing role, is rejected on its own while the other entries of the chunk apply.
 *
 * <p>Only the broker itself writes policy updates, on the deployment partition, so the processor
 * checks no user authorization. It rejects a command that came from a client request or that was
 * written to another partition.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateApplyChunkProcessor
    implements TypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateCommandMapper commandMapper;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateApplyChunkProcessor(
      final PolicyUpdateCommandMapper commandMapper,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.commandMapper = commandMapper;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyUpdateRecord> command) {
    final var chunk = command.getValue();
    if (command.hasRequestMetadata()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.FORBIDDEN,
          "Expected policy update '%s' to be written by the broker, but it was sent as a client request"
              .formatted(chunk.getUpdateId()));
      return;
    }
    if (command.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          "Expected policy update '%s' to be applied on partition %d, but it was written to partition %d"
              .formatted(
                  chunk.getUpdateId(), Protocol.DEPLOYMENT_PARTITION, command.getPartitionId()));
      return;
    }
    if (chunk.getChunkIndex() != 0 || chunk.getChunkCount() != 1) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected policy update '%s' to consist of a single chunk, but got chunk %d of %d"
              .formatted(chunk.getUpdateId(), chunk.getChunkIndex(), chunk.getChunkCount()));
      return;
    }

    final var commands = commandMapper.toCommands(chunk);
    if (commands.isLeft()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected every entry of policy update '%s' of policy '%s' to name its entity type, but: %s"
              .formatted(
                  chunk.getUpdateId(), chunk.getPolicyId(), String.join("; ", commands.getLeft())));
      return;
    }

    commands
        .get()
        .forEach(
            identityCommand ->
                commandWriter.appendFollowUpCommand(
                    identityCommand.key(), identityCommand.intent(), identityCommand.value()));

    // the entities are already in the follow-up commands; repeating them would write the chunk
    // into the same batch twice
    final var applied =
        new PolicyUpdateRecord()
            .setPolicyId(chunk.getPolicyId())
            .setPolicyVersion(chunk.getPolicyVersion())
            .setBasePolicyVersion(chunk.getBasePolicyVersion())
            .setSeqNo(chunk.getSeqNo())
            .setUpdateId(chunk.getUpdateId())
            .setChunkIndex(chunk.getChunkIndex())
            .setChunkCount(chunk.getChunkCount());
    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), PolicyUpdateIntent.CHUNK_APPLIED, applied);
  }
}
