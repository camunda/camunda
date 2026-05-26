/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

/**
 * Consumes a {@link MessageStartProcessInstanceRequestIntent#SWEEP_TOMBSTONES} trigger and emits
 * one {@link MessageStartProcessInstanceRequestIntent#TOMBSTONE_DELETED} event per past-deadline
 * tombstone entry in the cross-partition message-start dedup state on {@code P_B}. Each event's
 * applier removes both the forward and reverse dedup column families for its {@code
 * (processDefinitionKey, messageKey)} pair.
 *
 * <p>Each cycle is bounded by {@code batchLimit}; if more past-deadline entries remain, a follow-up
 * {@code SWEEP_TOMBSTONES} command is written to continue draining on the next stream iteration.
 * Mirrors the trigger-then-batch pattern of {@link MessageBatchExpireProcessor}.
 */
@ExcludeAuthorizationCheck
public final class MessageStartDedupTombstoneSweepProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MessageStartProcessInstanceDedupState dedupState;
  private final int batchLimit;
  private final InstantSource clock;

  public MessageStartDedupTombstoneSweepProcessor(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final MessageStartProcessInstanceDedupState dedupState,
      final int batchLimit,
      final InstantSource clock) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.dedupState = dedupState;
    this.batchLimit = batchLimit;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var counter = new int[] {0};
    final var entry = new MessageStartProcessInstanceRequestRecord();
    dedupState.visitTombstonesPastDeadline(
        clock.millis(),
        (processDefinitionKey, messageKey) -> {
          if (counter[0] >= batchLimit) {
            return;
          }
          entry.reset();
          entry.setProcessDefinitionKey(processDefinitionKey).setMessageKey(messageKey);
          stateWriter.appendFollowUpEvent(
              record.getKey(), MessageStartProcessInstanceRequestIntent.TOMBSTONE_DELETED, entry);
          counter[0]++;
        });

    if (counter[0] >= batchLimit) {
      // Hit the batch ceiling: there may be more past-deadline tombstones. Schedule the next
      // cycle via a follow-up trigger command rather than waiting for the next scheduler tick.
      // A spurious follow-up when nothing remains is harmless — the processor visits nothing and
      // writes no events.
      commandWriter.appendFollowUpCommand(
          record.getKey(),
          MessageStartProcessInstanceRequestIntent.SWEEP_TOMBSTONES,
          new MessageStartProcessInstanceRequestRecord());
    }
  }
}
