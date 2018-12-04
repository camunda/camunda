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

import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.logstreams.state.StateLifecycleListener;
import java.util.Arrays;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class MessageState implements StateLifecycleListener {

  private static final byte[] MESSAGE_KEY_COLUMN_FAMILY_NAME = "messageStateMessageKey".getBytes();
  private static final byte[] MESSAGE_COLUMN_FAMILY_NAME = "messageStateMessages".getBytes();
  private static final byte[] DEADLINE_COLUMN_FAMILY_NAME = "messageStateDeadlines".getBytes();
  private static final byte[] MESSAGE_ID_COLUMN_FAMILY_NAME = "messageStateMessageIds".getBytes();
  private static final byte[] CORRELATED_MESSAGE_COLUMN_FAMILY_NAME =
      "correlatedMessages".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    MESSAGE_KEY_COLUMN_FAMILY_NAME,
    MESSAGE_COLUMN_FAMILY_NAME,
    DEADLINE_COLUMN_FAMILY_NAME,
    MESSAGE_ID_COLUMN_FAMILY_NAME,
    CORRELATED_MESSAGE_COLUMN_FAMILY_NAME
  };
  public static final String SUB_SUFFIX = "Message";

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final Message message = new Message();

  /**
   * <pre>message key -> message
   */
  private ColumnFamilyHandle messageKeyColumnFamily;
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
  /**
   * <pre>key | workflow instance key -> []
   *
   * check if a message is correlated to a workflow instance */
  private ColumnFamilyHandle correlatedMessageColumnFamily;

  private ZbRocksDb db;

  public static List<byte[]> getColumnFamilyNames() {
    return Arrays.asList(COLUMN_FAMILY_NAMES);
  }

  @Override
  public void onOpened(StateController stateController) {
    db = stateController.getDb();

    messageKeyColumnFamily = stateController.getColumnFamilyHandle(MESSAGE_KEY_COLUMN_FAMILY_NAME);
    messageColumnFamily = stateController.getColumnFamilyHandle(MESSAGE_COLUMN_FAMILY_NAME);
    deadlineColumnFamily = stateController.getColumnFamilyHandle(DEADLINE_COLUMN_FAMILY_NAME);
    messageIdColumnFamily = stateController.getColumnFamilyHandle(MESSAGE_ID_COLUMN_FAMILY_NAME);
    correlatedMessageColumnFamily =
        stateController.getColumnFamilyHandle(CORRELATED_MESSAGE_COLUMN_FAMILY_NAME);
  }

  public void put(final Message message) {
    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {

      message.write(valueBuffer, 0);
      batch.put(
          messageKeyColumnFamily, message.getKey(), valueBuffer.byteArray(), message.getLength());

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

  private int writeCorrelatedMessageKey(
      MutableDirectBuffer buffer, long messageKey, long workflowInstanceKey) {
    int offset = 0;

    buffer.putLong(offset, messageKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, workflowInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;
    return offset;
  }

  public void putMessageCorrelation(long messageKey, long workflowInstanceKey) {
    final int length = writeCorrelatedMessageKey(keyBuffer, messageKey, workflowInstanceKey);
    db.put(
        correlatedMessageColumnFamily,
        keyBuffer.byteArray(),
        0,
        length,
        EXISTENCE,
        0,
        EXISTENCE.length);
  }

  public boolean existMessageCorrelation(long messageKey, long workflowInstanceKey) {
    final int length = writeCorrelatedMessageKey(keyBuffer, messageKey, workflowInstanceKey);
    return db.exists(correlatedMessageColumnFamily, keyBuffer.byteArray(), 0, length);
  }

  public void visitMessages(
      final DirectBuffer name, final DirectBuffer correlationKey, final MessageVisitor visitor) {
    int offset = 0;
    final int prefixLength = name.capacity() + correlationKey.capacity();
    final MutableDirectBuffer prefixBuffer = new UnsafeBuffer(new byte[prefixLength]);

    prefixBuffer.putBytes(offset, name, 0, name.capacity());
    offset += name.capacity();

    prefixBuffer.putBytes(offset, correlationKey, 0, correlationKey.capacity());

    db.forEachPrefixed(
        messageColumnFamily,
        prefixBuffer,
        (entry, control) -> {
          iterateKeyBuffer.wrap(entry.getKey());

          final long messageKey = iterateKeyBuffer.getLong(prefixLength, STATE_BYTE_ORDER);
          readMessage(messageKey, message);

          final boolean visited = visitor.visit(message);
          if (!visited) {
            control.stop();
          }
        });
  }

  private boolean readMessage(long key, Message message) {
    final int readBytes = db.get(messageKeyColumnFamily, key, valueBuffer);

    if (readBytes > 0) {
      message.wrap(valueBuffer, 0, readBytes);
      return true;
    } else {
      return false;
    }
  }

  public void visitMessagesWithDeadlineBefore(final long timestamp, MessageVisitor visitor) {

    db.forEach(
        deadlineColumnFamily,
        (entry, control) -> {
          iterateKeyBuffer.wrap(entry.getKey());
          final long deadline = iterateKeyBuffer.getLong(0, STATE_BYTE_ORDER);

          boolean visited = false;
          if (deadline <= timestamp) {
            final long messageKey = iterateKeyBuffer.getLong(Long.BYTES, STATE_BYTE_ORDER);

            readMessage(messageKey, message);
            visited = visitor.visit(message);
          }
          if (!visited) {
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

    try (WriteOptions options = new WriteOptions();
        ZbWriteBatch batch = new ZbWriteBatch()) {

      batch.delete(messageKeyColumnFamily, key);

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

      final UnsafeBuffer prefixBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
      prefixBuffer.putLong(0, key, STATE_BYTE_ORDER);

      db.forEachPrefixed(
          correlatedMessageColumnFamily,
          prefixBuffer,
          (entry, control) -> {
            batch.delete(correlatedMessageColumnFamily, entry.getKey());
          });

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  public interface MessageVisitor {
    boolean visit(Message message);
  }
}
