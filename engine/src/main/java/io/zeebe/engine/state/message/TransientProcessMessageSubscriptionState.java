package io.zeebe.engine.state.message;

import io.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.zeebe.engine.state.immutable.ProcessMessageSubscriptionState.ProcessMessageSubscriptionVisitor;
import io.zeebe.engine.state.mutable.MutableTransientProcessMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

final class TransientProcessMessageSubscriptionState
    implements MutableTransientProcessMessageSubscriptionState {

  private final SortedSet<Entry> transientState =
      Collections.synchronizedSortedSet(new TreeSet<>());

  private final ProcessMessageSubscriptionState persistentState;

  TransientProcessMessageSubscriptionState(final ProcessMessageSubscriptionState persistentState) {
    this.persistentState = persistentState;
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    for (final Entry entry : transientState) {
      if (entry.commandSentTime >= deadline) {
        break;
      }

      final var subscription =
          persistentState.getSubscription(
              entry.elementInstanceKey, BufferUtil.wrapString(entry.messageName));

      visitor.visit(subscription);
    }
  }

  @Override
  public void updateSentTimeInTransaction(
      final ProcessMessageSubscription subscription, final long commandSentTime) {

    final var updatedEntry = new Entry(subscription.getRecord(), commandSentTime);

    final var existingEntry = findEqualEntry(updatedEntry);
    if (existingEntry != null) {
      existingEntry.setCommandSentTime(commandSentTime);
    }
  }

  public void add(final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    transientState.add(new Entry(record, commandSentTime));
  }

  public void remove(final ProcessMessageSubscriptionRecord record) {
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

    Entry(final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
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
