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

import io.zeebe.logstreams.state.StateController;
import io.zeebe.util.sched.clock.ActorClock;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class SubscriptionState<T extends Subscription> {
  private static final byte[] EXISTENCE = new byte[] {1};
  private static final int TIME_OFFSET = 0;
  public static final int KEY_OFFSET = TIME_OFFSET + Long.BYTES;
  public static final int TIME_LENGTH = Long.BYTES;

  private static final byte[] SUB_NAME = "subscription".getBytes();
  private static final byte[] SUB_SEND_TIME_NAME = "subSendTime".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {SUB_NAME, SUB_SEND_TIME_NAME};

  private final StateController rocksDbWrapper;

  private final ExpandableArrayBuffer keyBuffer;
  private final ExpandableArrayBuffer valueBuffer;
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final ColumnFamilyHandle subscriptionHandle;
  private final ColumnFamilyHandle subSendTimeHandle;

  private final Supplier<T> subscriptionInstanceSupplier;

  public SubscriptionState(StateController rocksDbWrapper, Supplier<T> subscriptionInstanceSupplier)
      throws Exception {
    this.rocksDbWrapper = rocksDbWrapper;
    this.subscriptionInstanceSupplier = subscriptionInstanceSupplier;

    keyBuffer = new ExpandableArrayBuffer();
    valueBuffer = new ExpandableArrayBuffer();

    subscriptionHandle = rocksDbWrapper.getColumnFamilyHandle(SUB_NAME);
    subSendTimeHandle = rocksDbWrapper.getColumnFamilyHandle(SUB_SEND_TIME_NAME);
  }

  public void put(final T subscription) {
    subscription.writeCommandSentTime(keyBuffer, TIME_OFFSET);
    subscription.writeKey(keyBuffer, KEY_OFFSET);
    subscription.write(valueBuffer, 0);

    final int subscriptionLength = subscription.getLength();
    final int keyLength = subscription.getKeyLength();
    final int keyLengthWithTimePrefix = TIME_LENGTH + keyLength;
    writeKeyWithValue(subscriptionHandle, keyLength, subscriptionLength);
    writeKeyWithTimePrefix(subSendTimeHandle, keyLengthWithTimePrefix);
  }

  public void updateCommandSentTime(final T subscription) {
    remove(subscription);
    subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    put(subscription);
  }

  private void writeKeyWithValue(
      final ColumnFamilyHandle handle, final int keyLength, final int valueLength) {
    rocksDbWrapper.put(
        handle,
        keyBuffer.byteArray(),
        KEY_OFFSET,
        keyLength,
        valueBuffer.byteArray(),
        0,
        valueLength);
  }

  private void writeKeyWithTimePrefix(final ColumnFamilyHandle handle, final int keyLength) {
    rocksDbWrapper.put(
        handle, keyBuffer.byteArray(), TIME_OFFSET, keyLength, EXISTENCE, 0, EXISTENCE.length);
  }

  public T getSubscription(T subscription) {
    subscription.writeKey(keyBuffer, KEY_OFFSET);
    return getSubscription(keyBuffer, KEY_OFFSET, subscription.getKeyLength());
  }

  public T getSubscription(final DirectBuffer buffer, final int offset, final int length) {
    final int valueBufferSize = valueBuffer.capacity();
    final int readBytes =
        rocksDbWrapper.get(
            subscriptionHandle,
            buffer.byteArray(),
            offset,
            length,
            valueBuffer.byteArray(),
            0,
            valueBufferSize);

    if (readBytes > valueBufferSize) {
      valueBuffer.checkLimit(readBytes);
      // try again
      return getSubscription(buffer, offset, length);
    } else if (readBytes <= 0) {
      return null;
    } else {

      final T subscription = subscriptionInstanceSupplier.get();
      subscription.wrap(valueBuffer, 0, readBytes);

      return subscription;
    }
  }

  public List<T> findSubscriptions(
      final DirectBuffer messageName, final DirectBuffer correlationKey) {
    final List<T> subscriptionsList = new ArrayList<>();
    rocksDbWrapper.foreach(
        subscriptionHandle,
        (key, value) -> {
          final T subscription = subscriptionInstanceSupplier.get();
          subscription.wrap(new UnsafeBuffer(value), 0, value.length);

          if (messageName.equals(subscription.getMessageName())
              && correlationKey.equals(subscription.getCorrelationKey())) {
            subscriptionsList.add(subscription);
          }
        });
    return subscriptionsList;
  }

  public List<T> findSubscriptionBefore(final long deadline) {
    final List<T> subscriptionsList = new ArrayList<>();
    rocksDbWrapper.foreach(
        subSendTimeHandle,
        (key, value) -> {
          iterateKeyBuffer.wrap(key);
          final long time = iterateKeyBuffer.getLong(TIME_OFFSET, ByteOrder.LITTLE_ENDIAN);

          if (time > 0 && time < deadline) {
            final int keyLengthWithoutTime = key.length - KEY_OFFSET;
            subscriptionsList.add(
                getSubscription(iterateKeyBuffer, KEY_OFFSET, keyLengthWithoutTime));
          }
        });
    return subscriptionsList;
  }

  public boolean exist(final T subscription) {
    subscription.writeKey(keyBuffer, KEY_OFFSET);
    final int keyLength = subscription.getKeyLength();

    return rocksDbWrapper.exist(subscriptionHandle, keyBuffer.byteArray(), KEY_OFFSET, keyLength);
  }

  public void remove(final T subscription) {
    subscription.writeCommandSentTime(keyBuffer, TIME_OFFSET);
    subscription.writeKey(keyBuffer, KEY_OFFSET);

    final int keyLength = subscription.getKeyLength();
    rocksDbWrapper.remove(subscriptionHandle, keyBuffer.byteArray(), KEY_OFFSET, keyLength);

    final int keyLengthWithTimePrefix = TIME_LENGTH + keyLength;
    rocksDbWrapper.remove(
        subSendTimeHandle, keyBuffer.byteArray(), TIME_OFFSET, keyLengthWithTimePrefix);
  }
}
