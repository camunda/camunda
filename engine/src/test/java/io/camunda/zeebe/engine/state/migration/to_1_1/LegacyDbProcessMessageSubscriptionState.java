/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class LegacyDbProcessMessageSubscriptionState {

  private final TransactionContext transactionContext;

  // (elementInstanceKey, messageName) => ProcessMessageSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final LegacyProcessMessageSubscription processMessageSubscription;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, LegacyProcessMessageSubscription>
      subscriptionColumnFamily;

  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong sentTime;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>> sentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      sentTimeColumnFamily;

  public LegacyDbProcessMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    elementInstanceKey = new DbLong();
    messageName = new DbString();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    processMessageSubscription = new LegacyProcessMessageSubscription();

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

  public void put(
      final long key, final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    wrapSubscriptionKeys(record.getElementInstanceKey(), record.getMessageNameBuffer());

    processMessageSubscription.reset();
    processMessageSubscription.setKey(key).setRecord(record).setCommandSentTime(commandSentTime);

    subscriptionColumnFamily.upsert(elementKeyAndMessageName, processMessageSubscription);

    sentTime.wrapLong(commandSentTime);
    sentTimeColumnFamily.upsert(sentTimeCompositeKey, DbNil.INSTANCE);
  }

  public void updateToOpenedState(final ProcessMessageSubscriptionRecord record) {
    update(record, s -> s.setRecord(record).setCommandSentTime(0).setOpened());
  }

  public void updateToClosingState(
      final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    update(record, s -> s.setRecord(record).setCommandSentTime(commandSentTime).setClosing());
  }

  public void updateSentTimeInTransaction(
      final LegacyProcessMessageSubscription subscription, final long commandSentTime) {
    transactionContext.runInTransaction(
        () -> update(subscription, s -> s.setCommandSentTime(commandSentTime)));
  }

  public boolean remove(final long elementInstanceKey, final DirectBuffer messageName) {
    final LegacyProcessMessageSubscription subscription =
        getSubscription(elementInstanceKey, messageName);
    final boolean found = subscription != null;
    if (found) {
      remove(subscription);
    }
    return found;
  }

  public LegacyProcessMessageSubscription getSubscription(
      final long elementInstanceKey, final DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  /*  @Override
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
              final LegacyProcessMessageSubscription subscription =
                  subscriptionColumnFamily.get(compositeKey.getSecond());

              return visitor.visit(subscription);
            }
            return false;
          });
    }
  */

  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  private void update(
      final ProcessMessageSubscriptionRecord record,
      final Consumer<LegacyProcessMessageSubscription> modifier) {
    final LegacyProcessMessageSubscription subscription =
        getSubscription(record.getElementInstanceKey(), record.getMessageNameBuffer());
    if (subscription == null) {
      return;
    }

    update(subscription, modifier);
  }

  private void update(
      final LegacyProcessMessageSubscription subscription,
      final Consumer<LegacyProcessMessageSubscription> modifier) {
    final long previousSentTime = subscription.getCommandSentTime();
    modifier.accept(subscription);

    wrapSubscriptionKeys(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());
    subscriptionColumnFamily.upsert(elementKeyAndMessageName, subscription);

    final long updatedSentTime = subscription.getCommandSentTime();
    if (updatedSentTime != previousSentTime) {
      if (previousSentTime > 0) {
        sentTime.wrapLong(previousSentTime);
        sentTimeColumnFamily.delete(sentTimeCompositeKey);
      }

      if (updatedSentTime > 0) {
        sentTime.wrapLong(updatedSentTime);
        sentTimeColumnFamily.upsert(sentTimeCompositeKey, DbNil.INSTANCE);
      }
    }
  }

  private void remove(final LegacyProcessMessageSubscription subscription) {
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
