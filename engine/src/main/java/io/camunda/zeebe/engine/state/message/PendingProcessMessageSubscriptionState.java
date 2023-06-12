/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState.ProcessMessageSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.buffer.BufferUtil;

final class PendingProcessMessageSubscriptionState {

  private final TransientPendingSubscriptionState transientState =
      new TransientPendingSubscriptionState();

  private final ProcessMessageSubscriptionState persistentState;

  PendingProcessMessageSubscriptionState(final ProcessMessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    for (final var pendingSubscription : transientState.getEntriesBefore(deadline)) {
      final var subscription =
          persistentState.getSubscription(
              pendingSubscription.elementInstanceKey(),
              BufferUtil.wrapString(pendingSubscription.messageName()));

      visitor.visit(subscription);
    }
  }

  void updateSentTime(final ProcessMessageSubscriptionRecord record, final long commandSentTime) {

    final var updatedEntry = buildCommandEntry(record);

    transientState.updateLastSentTime(updatedEntry, commandSentTime);
  }

  void add(final ProcessMessageSubscriptionRecord record) {
    add(record, ActorClock.currentTimeMillis());
  }

  void add(final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    transientState.add(buildCommandEntry(record), commandSentTime);
  }

  void remove(final ProcessMessageSubscriptionRecord record) {
    transientState.remove(buildCommandEntry(record));
  }

  private PendingSubscription buildCommandEntry(final ProcessMessageSubscriptionRecord record) {
    return new PendingSubscription(record.getElementInstanceKey(), record.getMessageName());
  }
}
