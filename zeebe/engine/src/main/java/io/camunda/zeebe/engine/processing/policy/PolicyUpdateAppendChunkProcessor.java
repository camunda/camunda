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
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Buffers a chunk of a policy update. Chunks may arrive in any order. Once the last missing chunk
 * is buffered, the processor starts applying the update with its first chunk.
 *
 * <p>The processor does not validate the chunks: it assumes that the broker sends every chunk of an
 * update with the update's identifiers, and one update at a time. A chunk that is sent again
 * replaces the buffered one. The processor checks no user authorization; see {@link
 * PolicyUpdateOrigin}.
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
    final var rejection = PolicyUpdateOrigin.checkInternal(command);
    if (rejection.isPresent()) {
      rejectionWriter.appendRejection(command, rejection.get().type(), rejection.get().reason());
      return;
    }

    final var chunk = command.getValue();
    final boolean wasComplete = hasAllChunksBuffered(chunk.getUpdateId());
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.CHUNK_APPENDED, chunk);

    if (!wasComplete && hasAllChunksBuffered(chunk.getUpdateId())) {
      commandWriter.appendFollowUpCommand(
          key, PolicyUpdateIntent.APPLY_CHUNK, PolicyUpdateRecords.withoutPolicyData(chunk, 0));
    }
  }

  private boolean hasAllChunksBuffered(final String updateId) {
    return policyUpdateState
        .getUpdate(updateId)
        .map(PersistedPolicyUpdate::hasAllChunksBuffered)
        .orElse(false);
  }
}
