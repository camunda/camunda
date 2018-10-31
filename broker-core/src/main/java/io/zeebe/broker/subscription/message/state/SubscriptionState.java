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

import io.zeebe.broker.workflow.state.PersistenceHelper;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class SubscriptionState<T extends Subscription> {
  private static final int TIME_OFFSET = 0;
  public static final int KEY_OFFSET = TIME_OFFSET + Long.BYTES;
  public static final int TIME_LENGTH = Long.BYTES;

  private static final String SUB_NAME = "subscriptionStateSubscription";
  private static final String SUB_SEND_TIME_NAME = "subscriptionStateSubSendTime";

  public static final byte[][] getColumnFamilyNames(String suffix) {
    final String subName = SUB_NAME + suffix;
    final String subSendTimeName = SUB_SEND_TIME_NAME + suffix;
    return new byte[][] {subName.getBytes(), subSendTimeName.getBytes()};
  }

  private final StateController rocksDbWrapper;

  private final ExpandableArrayBuffer keyBuffer;
  private final ExpandableArrayBuffer valueBuffer;
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final ColumnFamilyHandle subscriptionHandle;
  private final ColumnFamilyHandle subSendTimeHandle;

  private final Class<T> clazz;
  private final PersistenceHelper persistenceHelper;

  public SubscriptionState(StateController rocksDbWrapper, String suffix, Class<T> clazz) {
    this.rocksDbWrapper = rocksDbWrapper;
    this.persistenceHelper = new PersistenceHelper(rocksDbWrapper);
    this.clazz = clazz;

    keyBuffer = new ExpandableArrayBuffer();
    valueBuffer = new ExpandableArrayBuffer();

    subscriptionHandle = rocksDbWrapper.getColumnFamilyHandle((SUB_NAME + suffix).getBytes());
    subSendTimeHandle =
        rocksDbWrapper.getColumnFamilyHandle((SUB_SEND_TIME_NAME + suffix).getBytes());
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

  private T getSubscription(final DirectBuffer buffer, final int offset, final int length) {
    return persistenceHelper.getValueInstance(clazz, subscriptionHandle, buffer, offset, length);
  }

  public List<T> findSubscriptions(
      final DirectBuffer messageName, final DirectBuffer correlationKey) {
    final List<T> subscriptionsList = new ArrayList<>();

    rocksDbWrapper.foreach(
        subscriptionHandle,
        (key, value) -> {
          try {
            final T subscription = clazz.newInstance();
            subscription.wrap(new UnsafeBuffer(value), 0, value.length);

            final boolean isEqual =
                messageName.equals(subscription.getMessageName())
                    && correlationKey.equals(subscription.getCorrelationKey());
            if (isEqual) {
              subscriptionsList.add(subscription);
            }
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });

    return subscriptionsList;
  }

  public List<T> findSubscriptionBefore(final long deadline) {
    final List<T> subscriptionsList = new ArrayList<>();
    rocksDbWrapper.whileTrue(
        subSendTimeHandle,
        (key, value) -> {
          iterateKeyBuffer.wrap(key);
          final long time = iterateKeyBuffer.getLong(TIME_OFFSET, STATE_BYTE_ORDER);

          final boolean isDue = time > 0 && time < deadline;
          if (isDue) {
            final int keyLengthWithoutTime = key.length - KEY_OFFSET;
            subscriptionsList.add(
                getSubscription(iterateKeyBuffer, KEY_OFFSET, keyLengthWithoutTime));
          }
          return isDue;
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
