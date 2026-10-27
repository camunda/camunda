/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;

/**
 * Applier for {@link MessageStartCorrelationKeyLockReleaseIntent#PUSHED}.
 *
 * <p>Emitted on {@code P_B} when a cross-partition message-start holder instance completes or
 * terminates and its {@code RELEASE} has been pushed straight to {@code P_K}. Its only state effect
 * is to drop the holder-origin entry ({@link
 * MutableMessageState#removeCrossPartitionStartHolderOrigin}) that {@code P_B} kept for this
 * holder: the push has consumed it, so leaving it would leak one entry per cross-partition start.
 *
 * <p>The event key is the holder process-instance key, which is exactly the key the holder-origin
 * entry is stored under, so the removal needs nothing from the record body. The removal is guarded
 * against a missing entry (the underlying delete is a no-op if absent), tolerating a replayed
 * {@code PUSHED} whose entry was already removed.
 */
final class MessageStartCorrelationKeyLockReleasePushedV1Applier
    implements TypedEventApplier<
        MessageStartCorrelationKeyLockReleaseIntent, MessageStartCorrelationKeyLockReleaseRecord> {

  private final MutableMessageState messageState;

  MessageStartCorrelationKeyLockReleasePushedV1Applier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageStartCorrelationKeyLockReleaseRecord value) {
    // the event key is the holder process-instance key, which the origin entry is stored under
    messageState.removeCrossPartitionStartHolderOrigin(key);
  }
}
