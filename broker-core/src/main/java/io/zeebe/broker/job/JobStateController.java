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

import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.util.KeyStateController;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.rocksdb.ZbRocksDb.IteratorControl;
import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import io.zeebe.util.buffer.BufferWriter;
import java.io.File;
import java.time.Instant;
import java.util.List;
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
  public static final byte[][] COLUMN_FAMILY_NAMES = {
    STATES_COLUMN_FAMILY_NAME, DEADLINES_COLUMN_FAMILY_NAME
  };

  private static final DirectBuffer NULL = new UnsafeBuffer(new byte[] {0});

  private ColumnFamilyHandle defaultColumnFamily;
  private ColumnFamilyHandle statesColumnFamily;
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
    final DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, NULL);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void activate(final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    final long key = record.getKey(), deadline = record.getValue().getDeadline();
    final DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions().setSync(true);
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATABLE);
      batch.delete(statesColumnFamily, keyBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATED);
      batch.put(statesColumnFamily, keyBuffer, NULL);

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
    final DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATED);
      batch.delete(statesColumnFamily, keyBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, NULL);

      keyBuffer = getDeadlinesKey(key, record.getValue().getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    final long key = record.getKey();
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      batch.delete(defaultColumnFamily, keyBuffer);

      for (final State state : State.values()) {
        keyBuffer = getStatesKey(key, type, state);
        batch.delete(statesColumnFamily, keyBuffer);
      }

      keyBuffer = getDeadlinesKey(key, record.getValue().getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void fail(final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    final long key = record.getKey();
    final DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATED);
      batch.delete(statesColumnFamily, keyBuffer);

      final State newState = record.getValue().getRetries() > 0 ? State.ACTIVATABLE : State.FAILED;
      keyBuffer = getStatesKey(key, type, newState);
      batch.put(statesColumnFamily, keyBuffer, NULL);

      keyBuffer = getDeadlinesKey(key, record.getValue().getDeadline());
      batch.delete(deadlinesColumnFamily, keyBuffer);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void resolve(final TypedRecord<JobRecord> record) {
    final DirectBuffer type = record.getValue().getType();
    final long key = record.getKey();
    final DirectBuffer valueBuffer;
    DirectBuffer keyBuffer;

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      keyBuffer = getDefaultKey(key);
      valueBuffer = writeValue(record.getValue());
      batch.put(defaultColumnFamily, keyBuffer, valueBuffer);

      keyBuffer = getStatesKey(key, type, State.FAILED);
      batch.delete(statesColumnFamily, keyBuffer);

      keyBuffer = getStatesKey(key, type, State.ACTIVATABLE);
      batch.put(statesColumnFamily, keyBuffer, NULL);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void forEachTimedOutEntry(final Instant upperBound, final IteratorConsumer callback) {

    final long upperBoundMilli = upperBound.toEpochMilli();

    db.forEach(
        deadlinesColumnFamily,
        (e, c) -> {
          final long deadline = e.getKey().getLong(0);
          if (deadline < upperBoundMilli) {
            final DirectBuffer keyBuffer = new UnsafeBuffer(e.getKey(), Long.BYTES, Long.BYTES);
            callback.accept(keyBuffer.getLong(0), getJob(keyBuffer), c);
          } else {
            c.stop();
          }
        });
  }

  public boolean exists(final TypedRecord<JobRecord> record) {
    final DirectBuffer dbKey = getDefaultKey(record.getKey());
    return db.exists(defaultColumnFamily, dbKey);
  }

  public boolean exists(final TypedRecord<JobRecord> record, final State state) {
    final DirectBuffer dbKey = getStatesKey(record.getKey(), record.getValue().getType(), state);
    return db.exists(statesColumnFamily, dbKey);
  }

  /**
   * This currently duplicates code from ZbRocksDb#forEachPrefixed because we need to control when
   * to stop the loop. forEach methods should typically execute once forEach as expected, without
   * control, and an Iterator/Iterable implementation is what would be used for control over the
   * looping process. Since it would take more time to implement non-trivial ones, this should be
   * done later.
   *
   * <p>Additionally, since this method is only used to deal with subscriptions, it will be removed
   * eventually, so performance/readability/reuse here isn't critical.
   */
  public void activatableJobs(final DirectBuffer type, final IteratorConsumer callback) {
    final DirectBuffer prefix = getStatesPrefix(type, State.ACTIVATABLE);

    db.forEachPrefixed(
        statesColumnFamily,
        prefix,
        (e, c) -> {
          final DirectBuffer keyBuffer =
              new UnsafeBuffer(e.getKey(), prefix.capacity(), Long.BYTES);
          callback.accept(keyBuffer.getLong(0), getJob(keyBuffer), c);
        });
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

  private UnsafeBuffer getStatesKey(final long key, final DirectBuffer type, final State state) {
    final UnsafeBuffer prefix = getStatesPrefix(type, state);
    keyBuffer.putLong(prefix.capacity(), key);

    return new UnsafeBuffer(keyBuffer, prefix.wrapAdjustment(), prefix.capacity() + Long.BYTES);
  }

  private UnsafeBuffer getDeadlinesKey(final long key, final long deadline) {
    keyBuffer.putLong(0, deadline);
    keyBuffer.putLong(Long.BYTES, key);

    return new UnsafeBuffer(keyBuffer, 0, Long.BYTES * 2);
  }

  private UnsafeBuffer getStatesPrefix(final DirectBuffer type, final State state) {
    final int typeLength = type.capacity();
    keyBuffer.putByte(0, state.value);
    keyBuffer.putBytes(1, type, 0, typeLength);

    return new UnsafeBuffer(keyBuffer, 0, typeLength + 1);
  }

  private UnsafeBuffer writeValue(final BufferWriter writer) {
    writer.write(valueBuffer, 0);
    return new UnsafeBuffer(valueBuffer, 0, writer.getLength());
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

  @FunctionalInterface
  public interface IteratorConsumer {
    void accept(final long key, final JobRecord record, final IteratorControl control);
  }
}
