/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class DbProcessMessageSubscriptionState
    implements MutableProcessMessageSubscriptionState {

  private final TransactionContext transactionContext;

  // (elementInstanceKey, messageName) => ProcessMessageSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final ProcessMessageSubscription processMessageSubscription;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, ProcessMessageSubscription>
      subscriptionColumnFamily;

  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong sentTime;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>> sentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      sentTimeColumnFamily;

  public DbProcessMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    elementInstanceKey = new DbLong();
    messageName = new DbString();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    processMessageSubscription = new ProcessMessageSubscription();

    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyAndMessageName,
            processMessageSubscription);

    sentTime = new DbLong();
    sentTimeCompositeKey = new DbCompositeKey<>(sentTime, elementKeyAndMessageName);
    sentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME,
            transactionContext,
            sentTimeCompositeKey,
            DbNil.INSTANCE);
  }

  @Override
  public void put(
      final long key, final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    wrapSubscriptionKeys(record.getElementInstanceKey(), record.getMessageNameBuffer());

    processMessageSubscription.reset();
    processMessageSubscription.setKey(key).setRecord(record).setCommandSentTime(commandSentTime);

    subscriptionColumnFamily.put(elementKeyAndMessageName, processMessageSubscription);

    sentTime.wrapLong(commandSentTime);
    sentTimeColumnFamily.put(sentTimeCompositeKey, DbNil.INSTANCE);
  }

  @Override
  public void updateToOpenedState(final ProcessMessageSubscriptionRecord record) {
    update(record, s -> s.setRecord(record).setCommandSentTime(0).setOpened());
  }

  @Override
  public void updateToClosingState(
      final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    update(record, s -> s.setRecord(record).setCommandSentTime(commandSentTime).setClosing());
  }

  @Override
  public void updateSentTimeInTransaction(
      final ProcessMessageSubscription subscription, final long commandSentTime) {
    transactionContext.runInTransaction(
        () -> update(subscription, s -> s.setCommandSentTime(commandSentTime)));
  }

  @Override
  public boolean remove(final long elementInstanceKey, final DirectBuffer messageName) {
    final ProcessMessageSubscription subscription =
        getSubscription(elementInstanceKey, messageName);
    final boolean found = subscription != null;
    if (found) {
      remove(subscription);
    }
    return found;
  }

  @Override
  public ProcessMessageSubscription getSubscription(
      final long elementInstanceKey, final DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  @Override
  public void visitElementSubscriptions(
      final long elementInstanceKey, final ProcessMessageSubscriptionVisitor visitor) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);

    subscriptionColumnFamily.whileEqualPrefix(
        this.elementInstanceKey,
        (compositeKey, subscription) -> {
          visitor.visit(subscription);
        });
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    sentTimeColumnFamily.whileTrue(
        (compositeKey, nil) -> {
          final long commandSentTime = compositeKey.getFirst().getValue();
          if (commandSentTime < deadline) {
            final ProcessMessageSubscription subscription =
                subscriptionColumnFamily.get(compositeKey.getSecond());

            return visitor.visit(subscription);
          }
          return false;
        });
  }

  @Override
  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  private void update(
      final ProcessMessageSubscriptionRecord record,
      final Consumer<ProcessMessageSubscription> modifier) {
    final ProcessMessageSubscription subscription =
        getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());
    if (subscription == null) {
      return;
    }

    update(subscription, modifier);
  }

  private void update(
      final ProcessMessageSubscription subscription,
      final Consumer<ProcessMessageSubscription> modifier) {
    final long previousSentTime = subscription.getCommandSentTime();
    modifier.accept(subscription);

    wrapSubscriptionKeys(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());
    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);

    final long updatedSentTime = subscription.getCommandSentTime();
    if (updatedSentTime != previousSentTime) {
      if (previousSentTime > 0) {
        sentTime.wrapLong(previousSentTime);
        sentTimeColumnFamily.delete(sentTimeCompositeKey);
      }

      if (updatedSentTime > 0) {
        sentTime.wrapLong(updatedSentTime);
        sentTimeColumnFamily.put(sentTimeCompositeKey, DbNil.INSTANCE);
      }
    }
  }

  private void remove(final ProcessMessageSubscription subscription) {
    wrapSubscriptionKeys(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());

    subscriptionColumnFamily.delete(elementKeyAndMessageName);

    sentTime.wrapLong(subscription.getCommandSentTime());
    sentTimeColumnFamily.delete(sentTimeCompositeKey);
  }

  private void wrapSubscriptionKeys(final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
  }
}
