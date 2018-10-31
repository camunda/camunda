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

import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.rocksdb.ZbWriteBatch;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.logstreams.state.StateLifecycleListener;
import java.util.Arrays;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class WorkflowInstanceSubscriptionState implements StateLifecycleListener {

  private static final String COLUMN_FAMILY_PREFIX = "wf-sub-";

  private static final byte[] SUBSCRIPTIONS_COLUMN_FAMILY_NAME =
      (COLUMN_FAMILY_PREFIX + "by-key").getBytes();
  private static final byte[] SENT_TIME_COLUMN_FAMILY_NAME =
      (COLUMN_FAMILY_PREFIX + "by-sent-time").getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    SUBSCRIPTIONS_COLUMN_FAMILY_NAME, SENT_TIME_COLUMN_FAMILY_NAME
  };

  private ZbRocksDb db;
  private ColumnFamilyHandle subscriptionColumnFamily;
  private ColumnFamilyHandle sentTimeColumnFamily;

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final WorkflowInstanceSubscription subscription = new WorkflowInstanceSubscription();

  public static List<byte[]> getColumnFamilyNames() {
    return Arrays.asList(COLUMN_FAMILY_NAMES);
  }

  @Override
  public void onOpened(StateController stateController) {
    this.db = stateController.getDb();

    subscriptionColumnFamily =
        stateController.getColumnFamilyHandle(SUBSCRIPTIONS_COLUMN_FAMILY_NAME);
    sentTimeColumnFamily = stateController.getColumnFamilyHandle(SENT_TIME_COLUMN_FAMILY_NAME);
  }

  public void put(final WorkflowInstanceSubscription subscription) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      subscription.write(valueBuffer, 0);

      batch.put(
          subscriptionColumnFamily,
          subscription.getElementInstanceKey(),
          valueBuffer.byteArray(),
          subscription.getLength());

      final int keyLength = writeSentTimeKey(keyBuffer, subscription);
      batch.put(
          sentTimeColumnFamily, keyBuffer.byteArray(), keyLength, EXISTENCE, EXISTENCE.length);

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private int writeSentTimeKey(
      MutableDirectBuffer buffer, final WorkflowInstanceSubscription subscription) {
    int offset = 0;

    buffer.putLong(offset, subscription.getCommandSentTime(), STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, subscription.getElementInstanceKey(), STATE_BYTE_ORDER);
    offset += Long.BYTES;

    assert (2 * Long.BYTES) == offset
        : "Offset problem: offset is not equal to expected key length";
    return offset;
  }

  private boolean readSubscription(long key, WorkflowInstanceSubscription subscription) {
    final int readBytes = db.get(subscriptionColumnFamily, key, valueBuffer);
    if (readBytes > 0) {
      subscription.wrap(valueBuffer, 0, readBytes);
      return true;
    } else {
      return false;
    }
  }

  public WorkflowInstanceSubscription getSubscription(long elementInstanceKey) {
    final boolean found = readSubscription(elementInstanceKey, subscription);
    if (found) {
      return subscription;
    } else {
      return null;
    }
  }

  public void visitSubscriptionBefore(
      final long deadline, WorkflowInstanceSubscriptionVisitor visitor) {

    db.forEach(
        sentTimeColumnFamily,
        (entry, control) -> {
          iterateKeyBuffer.wrap(entry.getKey());

          final long sentTime = iterateKeyBuffer.getLong(0, STATE_BYTE_ORDER);

          boolean visited = false;
          if (sentTime < deadline) {
            final long subscriptionKey = iterateKeyBuffer.getLong(Long.BYTES, STATE_BYTE_ORDER);

            final boolean found = readSubscription(subscriptionKey, subscription);
            if (!found) {
              throw new IllegalStateException(
                  String.format(
                      "Expected to find subscription with key %d, but no subscription found",
                      subscriptionKey));
            }

            visited = visitor.visit(subscription);
          }

          if (!visited) {
            control.stop();
          }
        });
  }

  public void updateToOpenedState(
      final WorkflowInstanceSubscription subscription, int subscriptionPartitionId) {

    subscription.setOpened();
    subscription.setSubscriptionPartitionId(subscriptionPartitionId);

    updateSentTime(subscription, 0);
  }

  public void updateToClosingState(final WorkflowInstanceSubscription subscription, long sentTime) {

    subscription.setClosing();

    updateSentTime(subscription, sentTime);
  }

  public void updateSentTime(final WorkflowInstanceSubscription subscription, long sentTime) {

    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      if (subscription.getCommandSentTime() > 0) {
        final int keyLength = writeSentTimeKey(keyBuffer, subscription);
        batch.delete(sentTimeColumnFamily, keyBuffer.byteArray(), keyLength);
      }

      subscription.setCommandSentTime(sentTime);

      subscription.write(valueBuffer, 0);
      batch.put(
          subscriptionColumnFamily,
          subscription.getElementInstanceKey(),
          valueBuffer.byteArray(),
          subscription.getLength());

      if (sentTime > 0) {
        final int keyLength = writeSentTimeKey(keyBuffer, subscription);
        batch.put(
            sentTimeColumnFamily, keyBuffer.byteArray(), keyLength, EXISTENCE, EXISTENCE.length);
      }

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean existSubscriptionForElementInstance(final long elementInstanceKey) {
    return db.exists(subscriptionColumnFamily, elementInstanceKey);
  }

  public boolean remove(final long elementInstanceKey) {
    final boolean found = readSubscription(elementInstanceKey, subscription);
    if (found) {
      remove(subscription);
    }
    return found;
  }

  private void remove(final WorkflowInstanceSubscription subscription) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {

      batch.delete(subscriptionColumnFamily, subscription.getElementInstanceKey());

      if (subscription.getCommandSentTime() > 0) {
        final int keyLength = writeSentTimeKey(keyBuffer, subscription);
        batch.delete(sentTimeColumnFamily, keyBuffer.byteArray(), keyLength);
      }

      db.write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  public interface WorkflowInstanceSubscriptionVisitor {
    boolean visit(WorkflowInstanceSubscription subscription);
  }
}
