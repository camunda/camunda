/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState.MessageSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.buffer.BufferUtil;

final class PendingMessageSubscriptionState implements MutablePendingMessageSubscriptionState {

  private final TransientPendingSubscriptionState transientState =
      new TransientPendingSubscriptionState();

  private final MessageSubscriptionState persistentState;

  PendingMessageSubscriptionState(final MessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final MessageSubscriptionVisitor visitor) {

    for (final var pendingSubscription : transientState.getEntriesBefore(deadline)) {
      final var subscription =
          persistentState.get(
              pendingSubscription.elementInstanceKey(),
              BufferUtil.wrapString(pendingSubscription.messageName()));

      visitor.visit(subscription);
    }
  }

  @Override
  public void updateCommandSentTime(
      final MessageSubscriptionRecord record, final long commandSentTime) {

    final var updatedEntry = buildPendingSubscription(record);

    transientState.updateLastSentTime(updatedEntry, commandSentTime);
  }

  void add(final MessageSubscriptionRecord record) {
    add(record, ActorClock.currentTimeMillis());
  }

  void add(final MessageSubscriptionRecord record, final long commandSentTime) {
    transientState.add(buildPendingSubscription(record), commandSentTime);
  }

  void remove(final MessageSubscriptionRecord record) {
    transientState.remove(buildPendingSubscription(record));
  }

  private PendingSubscription buildPendingSubscription(final MessageSubscriptionRecord record) {
    return new PendingSubscription(record.getElementInstanceKey(), record.getMessageName());
  }
}
