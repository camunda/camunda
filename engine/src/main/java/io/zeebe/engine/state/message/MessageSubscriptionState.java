/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public class MessageSubscriptionState {

  private final DbContext dbContext;

  // (elementInstanceKey, messageName) => MessageSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final MessageSubscription messageSubscription;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, MessageSubscription>
      subscriptionColumnFamily;

  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong sentTime;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>> sentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      sentTimeColumnFamily;

  // (messageName, correlationKey, elementInstanceKey) => \0
  private final DbString correlationKey;
  private final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>
      nameCorrelationAndElementInstanceKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, DbNil>
      messageNameAndCorrelationKeyColumnFamily;

  public MessageSubscriptionState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    this.dbContext = dbContext;

    elementInstanceKey = new DbLong();
    messageName = new DbString();
    messageSubscription = new MessageSubscription();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
            dbContext,
            elementKeyAndMessageName,
            messageSubscription);

    sentTime = new DbLong();
    sentTimeCompositeKey = new DbCompositeKey<>(sentTime, elementKeyAndMessageName);
    sentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
            dbContext,
            sentTimeCompositeKey,
            DbNil.INSTANCE);

    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationAndElementInstanceKey =
        new DbCompositeKey<>(nameAndCorrelationKey, elementInstanceKey);
    messageNameAndCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
            dbContext,
            nameCorrelationAndElementInstanceKey,
            DbNil.INSTANCE);
  }

  public MessageSubscription get(long elementInstanceKey, DirectBuffer messageName) {
    this.messageName.wrapBuffer(messageName);
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  public void put(final MessageSubscription subscription) {
    elementInstanceKey.wrapLong(subscription.getElementInstanceKey());
    messageName.wrapBuffer(subscription.getMessageName());
    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);

    correlationKey.wrapBuffer(subscription.getCorrelationKey());
    messageNameAndCorrelationKeyColumnFamily.put(
        nameCorrelationAndElementInstanceKey, DbNil.INSTANCE);
  }

  public void visitSubscriptions(
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      MessageSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    this.correlationKey.wrapBuffer(correlationKey);

    messageNameAndCorrelationKeyColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          return visitMessageSubscription(elementKeyAndMessageName, visitor);
        });
  }

  private Boolean visitMessageSubscription(
      DbCompositeKey<DbLong, DbString> elementKeyAndMessageName,
      MessageSubscriptionVisitor visitor) {
    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    if (messageSubscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find subscription with key %d and %s, but no subscription found",
              elementKeyAndMessageName.getFirst().getValue(),
              elementKeyAndMessageName.getSecond()));
    }
    return visitor.visit(messageSubscription);
  }

  public void updateToCorrelatingState(
      final MessageSubscription subscription,
      DirectBuffer messageVariables,
      long sentTime,
      long messageKey) {
    subscription.setMessageVariables(messageVariables);
    subscription.setMessageKey(messageKey);
    updateSentTime(subscription, sentTime);
  }

  public void resetSentTime(MessageSubscription subscription) {
    updateSentTime(subscription, 0);
  }

  public void updateSentTimeInTransaction(final MessageSubscription subscription, long sentTime) {
    dbContext.runInTransaction((() -> updateSentTime(subscription, sentTime)));
  }

  public void updateSentTime(final MessageSubscription subscription, long sentTime) {
    elementInstanceKey.wrapLong(subscription.getElementInstanceKey());
    messageName.wrapBuffer(subscription.getMessageName());

    removeSubscriptionFromSentTimeColumnFamily(subscription);

    subscription.setCommandSentTime(sentTime);
    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);

    if (sentTime > 0) {
      this.sentTime.wrapLong(subscription.getCommandSentTime());
      sentTimeColumnFamily.put(sentTimeCompositeKey, DbNil.INSTANCE);
    }
  }

  public void visitSubscriptionBefore(final long deadline, MessageSubscriptionVisitor visitor) {
    sentTimeColumnFamily.whileTrue(
        (compositeKey, nil) -> {
          final long sentTime = compositeKey.getFirst().getValue();
          if (sentTime < deadline) {
            return visitMessageSubscription(compositeKey.getSecond(), visitor);
          }
          return false;
        });
  }

  public boolean existSubscriptionForElementInstance(
      long elementInstanceKey, DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  public boolean remove(long elementInstanceKey, DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    final boolean found = messageSubscription != null;
    if (found) {
      remove(messageSubscription);
    }
    return found;
  }

  public void remove(final MessageSubscription subscription) {
    subscriptionColumnFamily.delete(elementKeyAndMessageName);

    messageName.wrapBuffer(subscription.getMessageName());
    correlationKey.wrapBuffer(subscription.getCorrelationKey());
    messageNameAndCorrelationKeyColumnFamily.delete(nameCorrelationAndElementInstanceKey);

    removeSubscriptionFromSentTimeColumnFamily(subscription);
  }

  private void removeSubscriptionFromSentTimeColumnFamily(MessageSubscription subscription) {
    if (subscription.getCommandSentTime() > 0) {
      sentTime.wrapLong(subscription.getCommandSentTime());
      sentTimeColumnFamily.delete(sentTimeCompositeKey);
    }
  }

  @FunctionalInterface
  public interface MessageSubscriptionVisitor {
    boolean visit(MessageSubscription subscription);
  }
}
