/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class keeps track of the last sent time of commands related to either message subscriptions
 * or process message subscriptions.
 */
final class TransientSubscriptionCommandState {

  private final List<CommandEntry> transientState = new ArrayList<>();

  final void add(final CommandEntry commandEntryToAdd) {
    removeEqualEntry(commandEntryToAdd);
    synchronized (transientState) {
      transientState.add(commandEntryToAdd);
    }
  }

  final void updateCommandSentTime(final CommandEntry updatedCommandEntry) {
    final var existingEntry = removeEqualEntry(updatedCommandEntry);
    if (existingEntry != null) {
      add(updatedCommandEntry);
    }
  }

  final void remove(final CommandEntry templateOfCommandEntryToBeRemoved) {
    removeEqualEntry(templateOfCommandEntryToBeRemoved);
  }

  private CommandEntry removeEqualEntry(final CommandEntry templateCommandEntry) {
    synchronized (transientState) {
      for (final Iterator<CommandEntry> i = transientState.iterator(); i.hasNext(); ) {
        final var entryToCompare = i.next();
        if (entryToCompare.equals(templateCommandEntry)) {
          i.remove();
          return entryToCompare;
        }
      }
    }
    return null;
  }

  final Iterable<CommandEntry> getEntriesBefore(final long deadline) {
    final var result = new ArrayList<CommandEntry>();
    synchronized (transientState) {
      Collections.sort(transientState);
      for (final CommandEntry commandEntry : transientState) {
        if (commandEntry.commandSentTime >= deadline) {
          break;
        }

        result.add(commandEntry);
      }
    }
    return result;
  }

  static final class CommandEntry implements Comparable<CommandEntry> {

    private final long elementInstanceKey;
    private final String messageName;
    private final long commandSentTime;

    CommandEntry(
        final long elementInstanceKey, final String messageName, final long commandSentTime) {
      this.elementInstanceKey = elementInstanceKey;
      this.messageName = messageName;
      this.commandSentTime = commandSentTime;
    }

    public long getElementInstanceKey() {
      return elementInstanceKey;
    }

    public String getMessageName() {
      return messageName;
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

      final CommandEntry commandEntry = (CommandEntry) o;

      if (elementInstanceKey != commandEntry.elementInstanceKey) {
        return false;
      }
      return messageName.equals(commandEntry.messageName);
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
    public int compareTo(final CommandEntry commandEntry) {
      return (int) (commandSentTime - commandEntry.commandSentTime);
    }
  }
}
