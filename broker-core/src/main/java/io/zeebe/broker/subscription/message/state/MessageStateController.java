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
package io.zeebe.broker.subscription.message.state;

import static io.zeebe.broker.workflow.state.PersistenceHelper.EXISTENCE;
import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.util.KeyStateController;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

public class MessageStateController extends KeyStateController {

  private static final byte[] MESSAGE_COLUMN_FAMILY_NAME = "messages".getBytes();
  private static final byte[] DEADLINE_COLUMN_FAMILY_NAME = "deadlines".getBytes();
  private static final byte[] MESSAGE_ID_COLUMN_FAMILY_NAME = "messageIds".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    MESSAGE_COLUMN_FAMILY_NAME, DEADLINE_COLUMN_FAMILY_NAME, MESSAGE_ID_COLUMN_FAMILY_NAME
  };

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final Message message = new Message();

  /**
   * <pre>message key -> message
   */
  private ColumnFamilyHandle defaultColumnFamily;
  /**
   * <pre>name | correlation key | key -> []
   *
   * find message by name and correlation key - the message key ensures the queue ordering
   */
  private ColumnFamilyHandle messageColumnFamily;
  /**
   * <pre>deadline | key -> []
   *
   * find messages which are before a given timestamp */
  private ColumnFamilyHandle deadlineColumnFamily;
  /**
   * <pre>name | correlation key | message id -> []
   *
   * exist a message for a given message name, correlation key and message id */
  private ColumnFamilyHandle messageIdColumnFamily;

  private SubscriptionState<MessageSubscription> subscriptionState;

  private ZbRocksDb db;

  @Override
  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    final List<byte[]> columnFamilyNames =
        Stream.of(COLUMN_FAMILY_NAMES, SubscriptionState.COLUMN_FAMILY_NAMES)
            .flatMap(Stream::of)
            .collect(Collectors.toList());

    final RocksDB rocksDB = super.open(dbDirectory, reopen, columnFamilyNames);

    defaultColumnFamily = rocksDB.getDefaultColumnFamily();
    messageColumnFamily = getColumnFamilyHandle(MESSAGE_COLUMN_FAMILY_NAME);
    deadlineColumnFamily = getColumnFamilyHandle(DEADLINE_COLUMN_FAMILY_NAME);
    messageIdColumnFamily = getColumnFamilyHandle(MESSAGE_ID_COLUMN_FAMILY_NAME);

    subscriptionState = new SubscriptionState<>(this, MessageSubscription.class);

    return rocksDB;
  }

  @Override
  protected RocksDB openDb(DBOptions dbOptions) throws RocksDBException {
    db =
        ZbRocksDb.open(
            dbOptions, dbDirectory.getAbsolutePath(), columnFamilyDescriptors, columnFamilyHandles);
    return db;
  }

  public void put(final Message message) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      message.write(valueBuffer, 0);
      batch.put(
          defaultColumnFamily, message.getKey(), valueBuffer.byteArray(), message.getLength());

      int length = writeMessageKey(keyBuffer, message);
      batch.put(messageColumnFamily, keyBuffer.byteArray(), length, EXISTENCE, EXISTENCE.length);

      length = writeDeadlineKey(keyBuffer, message);
      batch.put(deadlineColumnFamily, keyBuffer.byteArray(), length, EXISTENCE, EXISTENCE.length);

      if (message.getId().capacity() > 0) {
        length =
            writeMessageIdKey(
                keyBuffer, message.getName(), message.getCorrelationKey(), message.getId());
        batch.put(
            messageIdColumnFamily, keyBuffer.byteArray(), length, EXISTENCE, EXISTENCE.length);
      }

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private int writeMessageKey(MutableDirectBuffer buffer, final Message message) {
    int offset = 0;

    final DirectBuffer name = message.getName();
    buffer.putBytes(offset, name, 0, name.capacity());
    offset += name.capacity();

    final DirectBuffer correlationKey = message.getCorrelationKey();
    buffer.putBytes(offset, correlationKey, 0, correlationKey.capacity());
    offset += correlationKey.capacity();

    buffer.putLong(offset, message.getKey(), STATE_BYTE_ORDER);
    offset += Long.BYTES;

    assert (name.capacity() + correlationKey.capacity() + Long.BYTES) == offset
        : "Offset problem: offset is not equal to expected key length";
    return offset;
  }

  private int writeDeadlineKey(MutableDirectBuffer buffer, final Message message) {
    int offset = 0;

    buffer.putLong(0, message.getDeadline(), STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, message.getKey(), STATE_BYTE_ORDER);
    offset += Long.BYTES;

    assert (2 * Long.BYTES) == offset
        : "Offset problem: offset is not equal to expected key length";
    return offset;
  }

  private int writeMessageIdKey(
      MutableDirectBuffer buffer,
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final DirectBuffer messageId) {
    int offset = 0;

    buffer.putBytes(offset, name, 0, name.capacity());
    offset += name.capacity();
    buffer.putBytes(offset, correlationKey, 0, correlationKey.capacity());
    offset += correlationKey.capacity();

    buffer.putBytes(offset, messageId, 0, messageId.capacity());
    offset += messageId.capacity();
    return offset;
  }

  public Message findFirstMessage(final DirectBuffer name, final DirectBuffer correlationKey) {
    int offset = 0;
    final int prefixLength = name.capacity() + correlationKey.capacity();
    final MutableDirectBuffer prefixBuffer = new UnsafeBuffer(new byte[prefixLength]);

    prefixBuffer.putBytes(offset, name, 0, name.capacity());
    offset += name.capacity();

    prefixBuffer.putBytes(offset, correlationKey, 0, correlationKey.capacity());

    final AtomicBoolean found = new AtomicBoolean();

    db.forEachPrefixed(
        messageColumnFamily,
        prefixBuffer,
        (entry, control) -> {
          iterateKeyBuffer.wrap(entry.getKey());

          final long messageKey = iterateKeyBuffer.getLong(prefixLength, STATE_BYTE_ORDER);
          found.set(readMessage(messageKey, message));

          control.stop();
        });

    if (found.get()) {
      return message;
    } else {
      return null;
    }
  }

  private boolean readMessage(long key, Message message) {
    final int readBytes = db.get(defaultColumnFamily, key, valueBuffer);

    if (readBytes > 0) {
      message.wrap(valueBuffer, 0, readBytes);
      return true;
    } else {
      return false;
    }
  }

  public void findMessagesWithDeadlineBefore(final long timestamp, IteratorConsumer consumer) {

    db.forEach(
        deadlineColumnFamily,
        (entry, control) -> {
          iterateKeyBuffer.wrap(entry.getKey());
          final long deadline = iterateKeyBuffer.getLong(0, STATE_BYTE_ORDER);

          boolean consumed = false;
          if (deadline <= timestamp) {
            final long messageKey = iterateKeyBuffer.getLong(Long.BYTES, STATE_BYTE_ORDER);

            readMessage(messageKey, message);
            consumed = consumer.accept(message);
          }
          if (!consumed) {
            control.stop();
          }
        });
  }

  public boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    final int length = writeMessageIdKey(keyBuffer, name, correlationKey, messageId);

    return db.exists(messageIdColumnFamily, keyBuffer.byteArray(), 0, length);
  }

  public void remove(final long key) {
    final boolean exist = readMessage(key, message);
    if (!exist) {
      return;
    }

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      batch.delete(defaultColumnFamily, key);

      int length = writeMessageKey(keyBuffer, message);
      batch.delete(messageColumnFamily, keyBuffer.byteArray(), length);

      length = writeDeadlineKey(keyBuffer, message);
      batch.delete(deadlineColumnFamily, keyBuffer.byteArray(), length);

      if (message.getId().capacity() > 0) {
        length =
            writeMessageIdKey(
                keyBuffer, message.getName(), message.getCorrelationKey(), message.getId());
        batch.delete(messageIdColumnFamily, keyBuffer.byteArray(), length);
      }
      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void put(final MessageSubscription subscription) {
    subscriptionState.put(subscription);
  }

  public void updateCommandSentTime(final MessageSubscription subscription) {
    subscriptionState.updateCommandSentTime(subscription);
  }

  public List<MessageSubscription> findSubscriptions(
      final DirectBuffer messageName, final DirectBuffer correlationKey) {
    return subscriptionState.findSubscriptions(messageName, correlationKey);
  }

  public List<MessageSubscription> findSubscriptionBefore(final long deadline) {
    return subscriptionState.findSubscriptionBefore(deadline);
  }

  public boolean exist(final MessageSubscription subscription) {
    return subscriptionState.exist(subscription);
  }

  public MessageSubscription findSubscription(MessageSubscriptionRecord record) {
    final MessageSubscription messageSubscription =
        new MessageSubscription(
            record.getWorkflowInstancePartitionId(),
            record.getWorkflowInstanceKey(),
            record.getActivityInstanceKey());
    return subscriptionState.getSubscription(messageSubscription);
  }

  public boolean remove(MessageSubscriptionRecord messageSubscriptionRecord) {
    final MessageSubscription persistedSubscription = findSubscription(messageSubscriptionRecord);

    final boolean exist = persistedSubscription != null;
    if (exist) {
      subscriptionState.remove(persistedSubscription);
    }
    return exist;
  }

  public void remove(MessageSubscription messageSubscription) {
    subscriptionState.remove(messageSubscription);
  }

  @FunctionalInterface
  public interface IteratorConsumer {
    boolean accept(Message message);
  }
}
