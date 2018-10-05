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
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class TimerInstanceState {

  private static final byte[] DEFAULT_COLUMN_FAMILY_NAME = "timers".getBytes();
  private static final byte[] DUE_DATES_COLUMN_FAMILY_NAME = "dueDates".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    DEFAULT_COLUMN_FAMILY_NAME, DUE_DATES_COLUMN_FAMILY_NAME
  };

  private final UnsafeBuffer defaultKeyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private final UnsafeBuffer dueDateKeyBuffer = new UnsafeBuffer(new byte[2 * Long.BYTES]);

  private final UnsafeBuffer valueBuffer = new UnsafeBuffer(new byte[TimerInstance.LENGTH]);

  private final TimerInstance timer = new TimerInstance();

  private long nextDueDate;

  /**
   * <pre> activity instance key -> timer
   */
  private ColumnFamilyHandle defaultColumnFamily;

  /**
   * <pre>due date | activity instance key -> []
   *
   * find timers which are before a given timestamp
   */
  private ColumnFamilyHandle dueDatesColumnFamily;

  private final StateController db;

  public TimerInstanceState(StateController db) {
    this.db = db;

    defaultColumnFamily = db.getColumnFamilyHandle(DEFAULT_COLUMN_FAMILY_NAME);
    dueDatesColumnFamily = db.getColumnFamilyHandle(DUE_DATES_COLUMN_FAMILY_NAME);
  }

  public void put(TimerInstance timer) {

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      defaultKeyBuffer.putLong(0, timer.getActivityInstanceKey(), STATE_BYTE_ORDER);
      timer.write(valueBuffer, 0);
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
    buffer.putLong(Long.BYTES, timer.getActivityInstanceKey(), STATE_BYTE_ORDER);
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
            final long activityInstanceKey = dueDateKeyBuffer.getLong(Long.BYTES, STATE_BYTE_ORDER);
            readTimer(activityInstanceKey, timer);
            consumed = consumer.accept(timer);
          }

          if (!consumed) {
            nextDueDate = dueDate;
          }
          return consumed;
        });

    return nextDueDate;
  }

  private boolean readTimer(long key, TimerInstance timer) {
    defaultKeyBuffer.putLong(0, key, STATE_BYTE_ORDER);
    final int readBytes =
        db.get(
            defaultColumnFamily,
            defaultKeyBuffer.byteArray(),
            0,
            defaultKeyBuffer.capacity(),
            valueBuffer.byteArray(),
            0,
            valueBuffer.capacity());
    if (readBytes > 0) {
      timer.wrap(valueBuffer, 0, readBytes);
      return true;
    } else {
      return false;
    }
  }

  public TimerInstance get(long activityInstanceKey) {
    final boolean found = readTimer(activityInstanceKey, timer);
    if (found) {
      return timer;
    } else {
      return null;
    }
  }

  public void remove(TimerInstance timer) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      defaultKeyBuffer.putLong(0, timer.getActivityInstanceKey(), STATE_BYTE_ORDER);
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
