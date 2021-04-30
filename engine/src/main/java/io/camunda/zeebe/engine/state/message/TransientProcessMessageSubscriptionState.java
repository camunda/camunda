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
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

final class TransientProcessMessageSubscriptionState {

  private final TransientSubscriptionCommandState transientState =
      new TransientSubscriptionCommandState();

  private final ProcessMessageSubscriptionState persistentState;

  TransientProcessMessageSubscriptionState(final ProcessMessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  final void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    for (final CommandEntry commandEntry : transientState.getEntriesBefore(deadline)) {
      final var subscription =
          persistentState.getSubscription(
              commandEntry.getElementInstanceKey(),
              BufferUtil.wrapString(commandEntry.getMessageName()));

      visitor.visit(subscription);
    }
  }

  final void updateCommandSentTime(
      final ProcessMessageSubscriptionRecord record, final long commandSentTime) {

    final var updatedEntry = buildCommandEntry(record, commandSentTime);

    transientState.updateCommandSentTime(updatedEntry);
  }

  final void add(final ProcessMessageSubscriptionRecord record) {
    transientState.add(buildCommandEntry(record, 0));
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
