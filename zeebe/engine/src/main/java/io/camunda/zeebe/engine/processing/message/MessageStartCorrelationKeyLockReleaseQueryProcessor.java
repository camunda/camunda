/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@link MessageStartCorrelationKeyLockReleaseIntent#QUERY} command on {@code P_B =
 * hash(businessId)}, the partition where a message-start instance created via the cross-partition
 * handshake actually runs.
 *
 * <p>{@code P_K} polls this processor to discover when such holder instances have completed, so it
 * can release the correlation-key locks it is holding for them. A query batches one holder per lock
 * entry {@code P_K} is polling for a given {@code P_B}; the processor answers per holder: a holder
 * is "still active" iff a local element instance exists for its key and the instance is not banned.
 * A banned holder is treated as gone, mirroring the banned-instance filter on the uniqueness check,
 * so a stuck holder does not block its correlation key forever.
 *
 * <p>The processor always acknowledges with {@link
 * MessageStartCorrelationKeyLockReleaseIntent#QUERIED} for an observable trail, and replies {@link
 * MessageStartCorrelationKeyLockReleaseIntent#RELEASE} back to {@code P_K} for each holder that is
 * gone. Holders that are still active produce no reply — {@code P_K} keeps polling them with
 * back-off until completion. Each reply is routed back to {@code P_K} via the partition encoded in
 * the query's {@code requestKey}.
 */
@ExcludeAuthorizationCheck
public final class MessageStartCorrelationKeyLockReleaseQueryProcessor
    implements TypedRecordProcessor<MessageStartCorrelationKeyLockReleaseRecord> {

  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;

  public MessageStartCorrelationKeyLockReleaseQueryProcessor(
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> record) {
    final var query = record.getValue();

    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartCorrelationKeyLockReleaseIntent.QUERIED, query);

    for (final var holder : query.getHolders()) {
      if (!isHolderActive(holder.getProcessInstanceKey())) {
        commandSender.sendCorrelationKeyLockRelease(query.getRequestKey(), holder);
      }
    }
  }

  /**
   * Returns {@code true} when the holder process instance is still running on this partition. A
   * banned holder is treated as gone so it cannot keep its correlation-key lock held indefinitely,
   * mirroring the lookup-time banned filter on the live-state uniqueness check.
   */
  private boolean isHolderActive(final long holderProcessInstanceKey) {
    if (bannedInstanceState.isProcessInstanceBanned(holderProcessInstanceKey)) {
      return false;
    }
    return elementInstanceState.getInstance(holderProcessInstanceKey) != null;
  }
}
