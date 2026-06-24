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
import java.time.Duration;
import java.time.InstantSource;
import org.agrona.collections.MutableLong;

/**
 * Consumes a {@link MessageStartProcessInstanceRequestIntent#SWEEP_EXPIRED_DEDUPS} trigger and
 * emits one {@link MessageStartProcessInstanceRequestIntent#EXPIRED_DEDUP_DELETED} event per
 * past-deadline dedup entry in the cross-partition message-start dedup state on {@code P_B}. Each
 * event's applier removes the dedup column-family entry for its {@code (processDefinitionKey,
 * messageKey)} pair. Deletion is purely deadline-driven; the naming uses "expired dedup entry" to
 * reflect the role the entries play for {@code P_K}'s retries (they exist to bound the retry
 * window), not a post-completion lifecycle state.
 *
 * <p>Each cycle is bounded by {@code batchLimit}; if more past-deadline entries remain, a follow-up
 * {@code SWEEP_EXPIRED_DEDUPS} command is written to continue draining on the next stream
 * iteration. Mirrors the trigger-then-batch pattern of {@link MessageBatchExpireProcessor}.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestSweepExpiredDedupsProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MessageStartProcessInstanceDedupState dedupState;
  private final int batchLimit;
  private final Duration retryGrace;
  private final InstantSource clock;

  public MessageStartProcessInstanceRequestSweepExpiredDedupsProcessor(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final MessageStartProcessInstanceDedupState dedupState,
      final int batchLimit,
      final Duration retryGrace,
      final InstantSource clock) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.dedupState = dedupState;
    this.batchLimit = batchLimit;
    this.retryGrace = retryGrace;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var visited = new MutableLong(0);
    final var entry = new MessageStartProcessInstanceRequestRecord();
    final boolean hasMore =
        dedupState.visitExpiredEntries(
            // keep a row for the grace window past its deadline so a near-deadline in-flight retry
            // can still be re-replied from the dedup (see the request processor's lookup grace)
            clock.millis() - retryGrace.toMillis(),
            (processDefinitionKey, messageKey) -> {
              if (visited.getAndIncrement() >= batchLimit) {
                return false;
              }
              entry.reset();
              entry.setProcessDefinitionKey(processDefinitionKey).setMessageKey(messageKey);
              stateWriter.appendFollowUpEvent(
                  record.getKey(),
                  MessageStartProcessInstanceRequestIntent.EXPIRED_DEDUP_DELETED,
                  entry);
              return true;
            });

    if (hasMore) {
      // Past-deadline dedup entries remain beyond what fit in this batch. Schedule the next cycle
      // via a follow-up trigger command rather than waiting for the next scheduler tick.
      commandWriter.appendFollowUpCommand(
          record.getKey(),
          MessageStartProcessInstanceRequestIntent.SWEEP_EXPIRED_DEDUPS,
          new MessageStartProcessInstanceRequestRecord());
    }
  }
}
