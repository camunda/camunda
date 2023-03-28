/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.InstantSource;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class DbProcessMessageSubscriptionState
    implements MutableProcessMessageSubscriptionState,
        MutablePendingProcessMessageSubscriptionState,
        StreamProcessorLifecycleAware {

  // (elementInstanceKey, messageName) => ProcessMessageSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final ProcessMessageSubscription processMessageSubscription;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, ProcessMessageSubscription>
      subscriptionColumnFamily;

  private final PendingProcessMessageSubscriptionState transientState;

  public DbProcessMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final InstantSource clock) {
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
    transientState = new PendingProcessMessageSubscriptionState(this, clock);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    subscriptionColumnFamily.forEach(
        subscription -> {
          if (subscription.isOpening() || subscription.isClosing()) {
            transientState.add(subscription.getRecord());
          }
        });
  }

  @Override
  public void put(final long key, final ProcessMessageSubscriptionRecord record) {
    wrapSubscriptionKeys(record.getElementInstanceKey(), record.getMessageNameBuffer());

    processMessageSubscription.reset();
    processMessageSubscription.setKey(key).setRecord(record);

    subscriptionColumnFamily.insert(elementKeyAndMessageName, processMessageSubscription);

    transientState.add(record);
  }

  @Override
  public void updateToOpeningState(final ProcessMessageSubscriptionRecord record) {
    update(record, s -> s.setRecord(record).setOpening());
    transientState.add(record);
  }

  @Override
  public void updateToOpenedState(final ProcessMessageSubscriptionRecord record) {
    update(record, s -> s.setRecord(record).setOpened());
    transientState.remove(record);
  }

  @Override
  public void updateToClosingState(final ProcessMessageSubscriptionRecord record) {
    update(record, s -> s.setRecord(record).setClosing());
    transientState.add(record);
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
  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    transientState.visitSubscriptionBefore(deadline, visitor);
  }

  @Override
  public void updateSentTime(
      final ProcessMessageSubscriptionRecord record, final long commandSentTime) {
    transientState.updateSentTime(record, commandSentTime);
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
    modifier.accept(subscription);

    wrapSubscriptionKeys(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());
    subscriptionColumnFamily.update(elementKeyAndMessageName, subscription);
  }

  private void remove(final ProcessMessageSubscription subscription) {
    wrapSubscriptionKeys(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());

    subscriptionColumnFamily.deleteExisting(elementKeyAndMessageName);

    transientState.remove(subscription.getRecord());
  }

  private void wrapSubscriptionKeys(final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
  }
}
