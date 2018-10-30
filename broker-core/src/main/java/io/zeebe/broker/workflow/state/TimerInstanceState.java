/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.state;

import static io.zeebe.broker.workflow.state.PersistenceHelper.EXISTENCE;
import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import io.zeebe.logstreams.state.StateController;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class TimerInstanceState {

  private static final byte[] DEFAULT_COLUMN_FAMILY_NAME = "timerInstanceStateTimers".getBytes();
  private static final byte[] DUE_DATES_COLUMN_FAMILY_NAME =
      "timerInstanceStateDueDates".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    DEFAULT_COLUMN_FAMILY_NAME, DUE_DATES_COLUMN_FAMILY_NAME
  };

  private final UnsafeBuffer defaultKeyBuffer =
      new UnsafeBuffer(new byte[TimerInstance.KEY_LENGTH]);
  private final UnsafeBuffer dueDateKeyBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + TimerInstance.KEY_LENGTH]);
  private final UnsafeBuffer activityKeyPrefixBuffer = new UnsafeBuffer(new byte[Long.BYTES]);

  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final TimerInstance timer = new TimerInstance();

  private long nextDueDate;

  /**
   * <pre> activity instance key -> timer
   */
  private final ColumnFamilyHandle defaultColumnFamily;

  /**
   * <pre>due date | activity instance key -> []
   *
   * find timers which are before a given timestamp
   */
  private final ColumnFamilyHandle dueDatesColumnFamily;

  private final StateController db;

  public TimerInstanceState(StateController db) {
    this.db = db;

    defaultColumnFamily = db.getColumnFamilyHandle(DEFAULT_COLUMN_FAMILY_NAME);
    dueDatesColumnFamily = db.getColumnFamilyHandle(DUE_DATES_COLUMN_FAMILY_NAME);
  }

  public void put(TimerInstance timer) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      timer.writeKey(defaultKeyBuffer, 0);
      final DirectBuffer valueBuffer = writeIntoValueBuffer(timer);
      batch.put(defaultColumnFamily, defaultKeyBuffer, valueBuffer);

      writeDueDateKey(dueDateKeyBuffer, timer);
      batch.put(
          dueDatesColumnFamily,
          dueDateKeyBuffer.byteArray(),
          dueDateKeyBuffer.capacity(),
          EXISTENCE,
          EXISTENCE.length);

      db.getDb().write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeDueDateKey(MutableDirectBuffer buffer, TimerInstance timer) {
    buffer.putLong(0, timer.getDueDate(), STATE_BYTE_ORDER);
    timer.writeKey(buffer, Long.BYTES);
  }

  private DirectBuffer writeIntoValueBuffer(TimerInstance timer) {
    timer.write(valueBuffer, 0);
    return new UnsafeBuffer(valueBuffer, 0, timer.getLength());
  }

  public long findTimersWithDueDateBefore(final long timestamp, IteratorConsumer consumer) {
    nextDueDate = -1L;

    db.whileTrue(
        dueDatesColumnFamily,
        (key, value) -> {
          dueDateKeyBuffer.wrap(key);
          final long dueDate = dueDateKeyBuffer.getLong(0, STATE_BYTE_ORDER);

          boolean consumed = false;
          if (dueDate <= timestamp) {
            final long elementInstanceKey = dueDateKeyBuffer.getLong(Long.BYTES, STATE_BYTE_ORDER);
            final long timerKey = dueDateKeyBuffer.getLong(2 * Long.BYTES, STATE_BYTE_ORDER);
            readTimer(elementInstanceKey, timerKey, timer);
            consumed = consumer.accept(timer);
          }

          if (!consumed) {
            nextDueDate = dueDate;
          }
          return consumed;
        });

    return nextDueDate;
  }

  private boolean readTimer(long elementInstanceKey, long timerKey, TimerInstance timer) {
    this.timer.setElementInstanceKey(elementInstanceKey);
    this.timer.setKey(timerKey);
    this.timer.writeKey(defaultKeyBuffer, 0);

    return readTimer(defaultKeyBuffer, timer);
  }

  private boolean readTimer(DirectBuffer keyBuffer, TimerInstance timer) {
    final int readBytes =
        db.get(
            defaultColumnFamily,
            keyBuffer.byteArray(),
            0,
            keyBuffer.capacity(),
            valueBuffer.byteArray(),
            0,
            valueBuffer.capacity());

    if (readBytes == RocksDB.NOT_FOUND) {
      return false;
    }

    timer.wrap(valueBuffer, 0, readBytes);
    return true;
  }

  /**
   * NOTE: the timer instance given to the consumer is shared and will be mutated on the next
   * iteration.
   */
  public void forEachTimerForActivity(long activityInstanceKey, Consumer<TimerInstance> action) {
    activityKeyPrefixBuffer.putLong(0, activityInstanceKey, STATE_BYTE_ORDER);
    db.whileEqualPrefix(
        defaultColumnFamily,
        activityKeyPrefixBuffer.byteArray(),
        (key, value) -> {
          if (readTimer(new UnsafeBuffer(key), timer)) {
            action.accept(timer);
          }
        });
  }

  public TimerInstance get(long elementInstanceKey, long timerKey) {
    final boolean found = readTimer(elementInstanceKey, timerKey, timer);
    if (found) {
      return timer;
    } else {
      return null;
    }
  }

  public void remove(TimerInstance timer) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      defaultKeyBuffer.putLong(0, timer.getElementInstanceKey(), STATE_BYTE_ORDER);
      batch.delete(defaultColumnFamily, defaultKeyBuffer);

      writeDueDateKey(dueDateKeyBuffer, timer);
      batch.delete(dueDatesColumnFamily, dueDateKeyBuffer);

      db.getDb().write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  public interface IteratorConsumer {
    boolean accept(final TimerInstance timer);
  }
}
