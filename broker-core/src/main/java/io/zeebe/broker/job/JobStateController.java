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
package io.zeebe.broker.job;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.util.KeyStateController;
import io.zeebe.logstreams.rocksdb.ZbIterator;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.rocksdb.ZbRocksEntry;
import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.buffer.BufferWriter;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class JobStateController extends KeyStateController {
  private static final byte[] STATES_COLUMN_FAMILY_NAME = getBytes("states");
  private static final byte[] DEADLINES_COLUMN_FAMILY_NAME = getBytes("deadlines");
  private static final byte[] ACTIVATABLE_COLUMN_FAMILY_NAME = getBytes("activatable");
  public static final byte[][] COLUMN_FAMILY_NAMES = {
    STATES_COLUMN_FAMILY_NAME, ACTIVATABLE_COLUMN_FAMILY_NAME, DEADLINES_COLUMN_FAMILY_NAME
  };

  private static final DirectBuffer NULL = new UnsafeBuffer(new byte[] {0});

  // key => job record value
  private ColumnFamilyHandle defaultColumnFamily;
  // key => job state
  private ColumnFamilyHandle statesColumnFamily;
  // type => [key]
  private ColumnFamilyHandle activatableColumnFamily;
  // timeout => key
  private ColumnFamilyHandle deadlinesColumnFamily;

  private MutableDirectBuffer keyBuffer;
  private MutableDirectBuffer valueBuffer;

  private ZbRocksDb db;

  @Override
  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    final List<byte[]> columnFamilyNames =
        Stream.of(COLUMN_FAMILY_NAMES).collect(Collectors.toList());

    final RocksDB rocksDB = super.open(dbDirectory, reopen, columnFamilyNames);
    keyBuffer = new ExpandableArrayBuffer();
    valueBuffer = new ExpandableArrayBuffer();

    defaultColumnFamily = rocksDB.getDefaultColumnFamily();
    statesColumnFamily = getColumnFamilyHandle(STATES_COLUMN_FAMILY_NAME);
    activatableColumnFamily = getColumnFamilyHandle(ACTIVATABLE_COLUMN_FAMILY_NAME);
    deadlinesColumnFamily = getColumnFamilyHandle(DEADLINES_COLUMN_FAMILY_NAME);

    return rocksDB;
  }

  @Override
  protected RocksDB openDb(DBOptions dbOptions) throws RocksDBException {
    db =
        ZbRocksDb.open(
            dbOptions, dbDirectory.getAbsolutePath(), columnFamilyDescriptors, columnFamilyHandles);
    return db;
  }

  public void create(final long key, final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    DirectBuffer keyBuffer;
    DirectBuffer valueBuffer;

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      valueBuffer = writeStatesValue(State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getActivatableKey(key, type);
      batch.put(activatableColumnFamily, keyBuffer, NULL);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void activate(final TypedRecord<JobRecord> record) {
    activate(record.getKey(), record.getValue());
  }

  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();

    DirectBuffer keyBuffer;
    DirectBuffer valueBuffer;

    try (WriteOptions options = new WriteOptions().setSync(true);
        ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record);
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      valueBuffer = writeStatesValue(State.ACTIVATED);
      batch.put(statesColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getActivatableKey(key, type);
      batch.delete(activatableColumnFamily, keyBuffer);

      keyBuffer = getDeadlinesKey(key, deadline);
      batch.put(deadlinesColumnFamily, keyBuffer, NULL);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void timeout(final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    final long key = record.getKey();
    DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {

      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      valueBuffer = writeStatesValue(State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getActivatableKey(key, type);
      batch.put(activatableColumnFamily, keyBuffer, NULL);

      keyBuffer = getDeadlinesKey(key, record.getValue().getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(long key, JobRecord value) {
    final DirectBuffer type = value.getType();
    DirectBuffer keyBuffer;

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      batch.delete(defaultColumnFamily, keyBuffer);

      batch.delete(statesColumnFamily, keyBuffer);

      final DirectBuffer activatableKey = getActivatableKey(key, type);
      batch.delete(activatableColumnFamily, activatableKey);

      keyBuffer = getDeadlinesKey(key, value.getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void fail(long key, JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();
    DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(updatedValue);
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      final State newState = updatedValue.getRetries() > 0 ? State.ACTIVATABLE : State.FAILED;

      valueBuffer = writeStatesValue(newState);
      batch.put(statesColumnFamily, keyBuffer, valueBuffer);

      if (newState == State.ACTIVATABLE) {
        keyBuffer = getActivatableKey(key, type);
        batch.put(activatableColumnFamily, keyBuffer, NULL);
      }

      keyBuffer = getDeadlinesKey(key, updatedValue.getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void resolve(long key, final JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();
    DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(updatedValue);
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      valueBuffer = writeStatesValue(State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getActivatableKey(key, type);
      batch.put(activatableColumnFamily, keyBuffer, NULL);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean exists(long jobKey) {
    final DirectBuffer dbKey = getDefaultKey(jobKey);
    return db.exists(defaultColumnFamily, dbKey);
  }

  public boolean isInState(long key, State state) {
    final DirectBuffer keyBuffer = getDefaultKey(key);
    if (db.exists(statesColumnFamily, keyBuffer, valueBuffer)) {
      return valueBuffer.getByte(0) == state.value;
    }

    return false;
  }

  public ZbIterator activatableJobs(final DirectBuffer type) {
    // clone buffer to not interfere when keyBuffer is reused inside callback
    final DirectBuffer prefix = cloneBuffer(getActivatablePrefix(type));
    return db.prefixedIterator(activatableColumnFamily, prefix);
  }

  public long getActivatableJobKey(final DirectBuffer keyBuffer) {
    return keyBuffer.getLong(keyBuffer.capacity() - Long.BYTES);
  }

  public void forEachTimedOutJobs(final Instant upperBound, Consumer<Entry> action) {
    Objects.requireNonNull(action);

    final long upperBoundMilli = upperBound.toEpochMilli();
    try (final ZbIterator iterator = db.iterator(deadlinesColumnFamily)) {
      for (final ZbRocksEntry entry : iterator) {
        final long deadline = entry.getKey().getLong(0);

        if (deadline >= upperBoundMilli) {
          break;
        }

        final DirectBuffer keyBuffer = new UnsafeBuffer(entry.getKey(), Long.BYTES, Long.BYTES);
        action.accept(new Entry(keyBuffer.getLong(0), getJob(keyBuffer)));
      }
    }
  }

  public JobRecord getJob(final long key) {
    final DirectBuffer keyBuffer = getDefaultKey(key);
    return getJob(keyBuffer);
  }

  private JobRecord getJob(final DirectBuffer keyBuffer) {
    final int bytesRead = db.get(defaultColumnFamily, keyBuffer, valueBuffer);
    return readJob(valueBuffer, bytesRead);
  }

  private JobRecord readJob(final DirectBuffer buffer, final int length) {
    final JobRecord record = new JobRecord();
    record.wrap(buffer, 0, length);

    return record;
  }

  private UnsafeBuffer getDefaultKey(final long key) {
    keyBuffer.putLong(0, key);
    return new UnsafeBuffer(keyBuffer, 0, Long.BYTES);
  }

  private UnsafeBuffer getActivatableKey(final long key, final DirectBuffer type) {
    final int typeLength = type.capacity();
    keyBuffer.putBytes(0, type, 0, typeLength);
    keyBuffer.putLong(typeLength, key);

    return new UnsafeBuffer(keyBuffer, 0, typeLength + Long.BYTES);
  }

  private UnsafeBuffer getActivatablePrefix(final DirectBuffer type) {
    final int typeLength = type.capacity();
    keyBuffer.putBytes(0, type, 0, typeLength);

    return new UnsafeBuffer(keyBuffer, 0, typeLength);
  }

  private UnsafeBuffer getDeadlinesKey(final long key, final long deadline) {
    keyBuffer.putLong(0, deadline);
    keyBuffer.putLong(Long.BYTES, key);

    return new UnsafeBuffer(keyBuffer, 0, Long.BYTES * 2);
  }

  private UnsafeBuffer writeValue(final BufferWriter writer) {
    writer.write(valueBuffer, 0);
    return new UnsafeBuffer(valueBuffer, 0, writer.getLength());
  }

  private DirectBuffer writeStatesValue(final State state) {
    valueBuffer.putByte(0, state.value);
    return new UnsafeBuffer(valueBuffer, 0, 1);
  }

  private Entry mapActivatableEntry(final ZbRocksEntry entry) {
    final DirectBuffer keyBuffer =
        new UnsafeBuffer(entry.getKey(), entry.getKey().capacity() - Long.BYTES, Long.BYTES);
    return new Entry(keyBuffer.getLong(0), getJob(keyBuffer));
  }

  public enum State {
    ACTIVATABLE((byte) 0),
    ACTIVATED((byte) 1),
    FAILED((byte) 2);

    byte value;

    State(byte value) {
      this.value = value;
    }
  }

  public static class Entry {
    public final long key;
    public final JobRecord record;

    public Entry(long key, JobRecord record) {
      this.key = key;
      this.record = record;
    }
  }
}
