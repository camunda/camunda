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
import io.camunda.zeebe.engine.state.message.TransientSubscriptionCommandState.CommandEntry;
import io.camunda.zeebe.engine.state.mutable.MutableTransientProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.sched.clock.ActorClock;

final class TransientProcessMessageSubscriptionState
    implements MutableTransientProcessMessageSubscriptionState {

  private final TransientSubscriptionCommandState transientState =
      new TransientSubscriptionCommandState();

  private final ProcessMessageSubscriptionState persistentState;

  TransientProcessMessageSubscriptionState(final ProcessMessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    for (final CommandEntry commandEntry : transientState.getEntriesBefore(deadline)) {
      final var subscription =
          persistentState.getSubscription(
              commandEntry.getElementInstanceKey(),
              BufferUtil.wrapString(commandEntry.getMessageName()));

      visitor.visit(subscription);
    }
  }

  @Override
  public final void updateSentTime(
      final ProcessMessageSubscription subscription, final long commandSentTime) {

    final var updatedEntry = buildCommandEntry(subscription.getRecord(), commandSentTime);

    transientState.updateCommandSentTime(updatedEntry);
  }

  final void add(final ProcessMessageSubscriptionRecord record) {
    add(record, ActorClock.currentTimeMillis());
  }

  final void add(final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    transientState.add(buildCommandEntry(record, commandSentTime));
  }

  final void remove(final ProcessMessageSubscriptionRecord record) {
    transientState.remove(buildCommandEntry(record, 0));
  }

  private CommandEntry buildCommandEntry(
      final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    return new CommandEntry(
        record.getElementInstanceKey(), record.getMessageName(), commandSentTime);
  }
}
