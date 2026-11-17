/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Applier for {@link MessageStartProcessInstanceRequestIntent#REQUESTED}.
 *
 * <p>The same intent fires on both partitions involved in the cross-partition message-start
 * handshake:
 *
 * <ul>
 *   <li><b>On {@code P_K}</b> (the partition that owns the message): emitted from the publish
 *       processor when it dispatches the cross-partition ask. The applier persists a pending-ask
 *       entry so the retry scheduler can re-send if the reply is dropped.
 *   <li><b>On {@code P_B}</b> (the partition handling the request): emitted by the request
 *       processor as an acknowledgement. There is no pending-ask state on {@code P_B} and nothing
 *       to persist.
 * </ul>
 *
 * <p>The partition is discriminated by {@code Protocol.decodePartitionId(messageKey)}, which always
 * points back to {@code P_K} because {@code messageKey} is generated when the message is first
 * published. Running the same applier on {@code P_B} for the same intent is a deliberate no-op
 * rather than a separate registration: the data shape is identical and the discrimination keeps the
 * applier idempotent regardless of which partition replays it.
 */
final class MessageStartProcessInstanceRequestedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final int partitionId;
  private final MutableMessageStartProcessInstanceAskState askState;
  private final MessageStartProcessInstanceAsk askBuffer = new MessageStartProcessInstanceAsk();

  MessageStartProcessInstanceRequestedV1Applier(
      final int partitionId, final MutableMessageStartProcessInstanceAskState askState) {
    this.partitionId = partitionId;
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    if (Protocol.decodePartitionId(value.getMessageKey()) != partitionId) {
      // We are on P_B (or any partition that did not originate the ask): nothing to track here,
      // the pending-ask state only exists on P_K.
      return;
    }
    askBuffer.reset();
    askBuffer.wrap(value);
    askState.put(askBuffer);
  }
}
