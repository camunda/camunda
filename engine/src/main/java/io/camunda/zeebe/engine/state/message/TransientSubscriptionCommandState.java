/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class keeps track of the last sent time of commands related to either message subscriptions
 * or process message subscriptions.
 *
 * <p><strong>Note:</strong> this class is not thread safe. It is assumed that it will be called
 * from a single thread only</b>
 */
public final class TransientSubscriptionCommandState {

  private final List<CommandEntry> transientState = new ArrayList<>();

  public final void add(final CommandEntry commandEntryToAdd) {
    removeEqualEntry(commandEntryToAdd);
    transientState.add(commandEntryToAdd);
  }

  public final void updateCommandSentTime(final CommandEntry updatedCommandEntry) {
    if (removeEqualEntry(updatedCommandEntry)) {
      add(updatedCommandEntry);
    }
  }

  public final void remove(final CommandEntry templateOfCommandEntryToBeRemoved) {
    removeEqualEntry(templateOfCommandEntryToBeRemoved);
  }

  private boolean removeEqualEntry(final CommandEntry templateCommandEntry) {
    return transientState.removeIf(templateCommandEntry::equals);
  }

  final Iterable<CommandEntry> getEntriesBefore(final long deadline) {
    return transientState.stream()
        .sorted()
        .takeWhile(commandEntry -> commandEntry.getCommandSentTime() < deadline)
        .collect(Collectors.toList());
  }

  public static final class CommandEntry implements Comparable<CommandEntry> {

    private final long elementInstanceKey;
    private final String messageName;
    private final long commandSentTime;

    public CommandEntry(
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

    public long getCommandSentTime() {
      return commandSentTime;
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
