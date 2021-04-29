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
import io.camunda.zeebe.engine.state.mutable.MutableTransientMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

final class TransientMessageSubscriptionState implements MutableTransientMessageSubscriptionState {

  private final SortedSet<Entry> transientState =
      Collections.synchronizedSortedSet(new TreeSet<>());

  private final MessageSubscriptionState persistentState;

  TransientMessageSubscriptionState(final MessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final MessageSubscriptionVisitor visitor) {

    for (final Entry entry : transientState) {
      if (entry.commandSentTime >= deadline) {
        break;
      }

      final var subscription =
          persistentState.get(entry.elementInstanceKey, BufferUtil.wrapString(entry.messageName));

      visitor.visit(subscription);
    }
  }

  @Override
  public void updateCommandSentTime(
      final MessageSubscriptionRecord record, final long commandSentTime) {

    final var updatedEntry = new Entry(record, commandSentTime);

    final var existingEntry = findEqualEntry(updatedEntry);
    if (existingEntry != null) {
      transientState.remove(existingEntry);
      existingEntry.setCommandSentTime(commandSentTime);
      transientState.add(existingEntry);
    }
  }

  public void add(final MessageSubscriptionRecord record) {
    transientState.add(new Entry(record, 0));
  }

  public void remove(final MessageSubscriptionRecord record) {
    final var existingEntry = findEqualEntry(new Entry(record, 0));

    if (existingEntry != null) {
      transientState.remove(existingEntry);
    }
  }

  private Entry findEqualEntry(final Entry entry) {
    for (final Entry entryToCompare : transientState) {
      if (entryToCompare.equals(entry)) {
        return entryToCompare;
      }
    }

    return null;
  }

  protected static final class Entry implements Comparable<Entry> {

    private final long elementInstanceKey;
    private final String messageName;
    private long commandSentTime;

    Entry(final MessageSubscriptionRecord record, final long commandSentTime) {
      elementInstanceKey = record.getElementInstanceKey();
      messageName = record.getMessageName();

      this.commandSentTime = commandSentTime;
    }

    private void setCommandSentTime(final long commandSentTime) {
      this.commandSentTime = commandSentTime;
    }

    @Override
    public int hashCode() {
      return (int) (elementInstanceKey ^ (elementInstanceKey >>> 32));
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Entry entry = (Entry) o;

      if (elementInstanceKey != entry.elementInstanceKey) {
        return false;
      }
      return messageName.equals(entry.messageName);
    }

    @Override
    public String toString() {
      return "Entry{"
          + "elementInstanceKey="
          + elementInstanceKey
          + ", messageName='"
          + messageName
          + '\''
          + ", commandSentTime="
          + commandSentTime
          + '}';
    }

    @Override
    public int compareTo(final Entry entry) {
      return (int) (commandSentTime - entry.commandSentTime);
    }
  }
}
