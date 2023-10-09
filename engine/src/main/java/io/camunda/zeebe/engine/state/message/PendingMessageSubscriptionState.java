/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState.MessageSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.TransientSubscriptionCommandState.CommandEntry;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.slf4j.Logger;

final class PendingMessageSubscriptionState implements MutablePendingMessageSubscriptionState {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private final TransientSubscriptionCommandState transientState =
      new TransientSubscriptionCommandState();

  private final MessageSubscriptionState persistentState;

  PendingMessageSubscriptionState(final MessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final MessageSubscriptionVisitor visitor) {

    for (final CommandEntry commandEntry : transientState.getEntriesBefore(deadline)) {
      final var subscription =
          persistentState.get(
              commandEntry.getElementInstanceKey(),
              BufferUtil.wrapString(commandEntry.getMessageName()));

      if (subscription == null) {
        // This case can occur while a scheduled job is running asynchronously
        // and the stream processor removes one of the returned subscriptions from the state.
        LOG.warn(
            "Expected to find a subscription with key {} and message name {}, but none found. The state is inconsistent.",
            commandEntry.getElementInstanceKey(),
            commandEntry.getMessageName());
      } else {
        visitor.visit(subscription);
      }
    }
  }

  @Override
  public void updateCommandSentTime(
      final MessageSubscriptionRecord record, final long commandSentTime) {

    final var updatedEntry = buildCommandEntry(record, commandSentTime);

    transientState.updateCommandSentTime(updatedEntry);
  }

  final void add(final MessageSubscriptionRecord record) {
    add(record, ActorClock.currentTimeMillis());
  }

  final void add(final MessageSubscriptionRecord record, final long commandSentTime) {
    transientState.add(buildCommandEntry(record, commandSentTime));
  }

  final void remove(final MessageSubscriptionRecord record) {
    transientState.remove(buildCommandEntry(record, 0));
  }

  private CommandEntry buildCommandEntry(
      final MessageSubscriptionRecord record, final long commandSentTime) {
    return new CommandEntry(
        record.getElementInstanceKey(), record.getMessageName(), commandSentTime);
  }
}
