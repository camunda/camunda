/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Applies a policy update that consists of a single chunk. The chunk is validated as a whole and
 * then turned into identity commands, which the identity processors apply and distribute to the
 * other partitions. An invalid chunk is rejected without writing any of its commands.
 */
@NullMarked
public final class PolicyUpdateApplyProcessor implements TypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateCommandMapper commandMapper;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateApplyProcessor(
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
    if (chunk.getChunkIndex() != 0 || chunk.getChunkCount() != 1) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected policy update '%s' to consist of a single chunk, but got chunk %d of %d"
              .formatted(chunk.getUpdateId(), chunk.getChunkIndex(), chunk.getChunkCount()));
      return;
    }

    final var errors = commandMapper.validate(chunk);
    if (!errors.isEmpty()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected policy update '%s' of policy '%s' to be applicable, but: %s"
              .formatted(chunk.getUpdateId(), chunk.getPolicyId(), String.join("; ", errors)));
      return;
    }

    commandMapper
        .toCommands(chunk)
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
            .setUpdateId(chunk.getUpdateId())
            .setChunkIndex(chunk.getChunkIndex())
            .setChunkCount(chunk.getChunkCount());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), PolicyUpdateIntent.APPLIED, applied);
  }
}
