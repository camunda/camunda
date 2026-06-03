/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;

/**
 * Applier for {@link MessageStartCorrelationKeyLockReleaseIntent#RELEASED}.
 *
 * <p>Emitted on {@code P_K} when it accepts a {@code RELEASE} reply from {@code P_B} telling it the
 * remote holder instance has completed. At this point of the increment chain the event is a no-op:
 * the {@code P_K}-side reply handling (accepting the response) and the actual lock release plus
 * buffered-message pick-up land in later commits. The applier is registered now so every event
 * intent has an applier from the moment the intent is introduced.
 */
final class MessageStartCorrelationKeyLockReleaseReleasedV1Applier
    implements TypedEventApplier<
        MessageStartCorrelationKeyLockReleaseIntent, MessageStartCorrelationKeyLockReleaseRecord> {

  @Override
  public void applyState(final long key, final MessageStartCorrelationKeyLockReleaseRecord value) {
    // no-op for now: the lock release and buffered-message pick-up land in a later commit
  }
}
