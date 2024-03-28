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
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DbProcessMessageSubscriptionState
    implements MutableProcessMessageSubscriptionState,
        PendingProcessMessageSubscriptionState,
        StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  // (elementInstanceKey, tenant aware messageName) => ProcessMessageSubscription
  private final DbLong subscriptionKey;
  private final DbLong elementInstanceKey;

  private final DbString tenantIdKey;
  private final DbString messageName;
  private final DbTenantAwareKey<DbString> tenantAwareMessageName;
  private final DbCompositeKey<DbLong, DbTenantAwareKey<DbString>> elementKeyAndMessageName;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, DbLong>
      elementKeyMessageNameAndSubscriptionKey;
  private final ProcessMessageSubscription processMessageSubscription;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, DbLong>,
          ProcessMessageSubscription>
      subscriptionColumnFamily;

  private final TransientPendingSubscriptionState transientState;

  public DbProcessMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    elementInstanceKey = new DbLong();
    subscriptionKey = new DbLong();
    tenantIdKey = new DbString();
    messageName = new DbString();
    tenantAwareMessageName = new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, tenantAwareMessageName);
    elementKeyMessageNameAndSubscriptionKey =
        new DbCompositeKey<>(elementKeyAndMessageName, subscriptionKey);
    processMessageSubscription = new ProcessMessageSubscription();
    transientState = transientProcessMessageSubscriptionState;

    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyMessageNameAndSubscriptionKey,
            processMessageSubscription);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    subscriptionColumnFamily.forEach(
        subscription -> {
          if (subscription.isOpening() || subscription.isClosing()) {
            final var record = subscription.getRecord();
            transientState.add(
                new PendingSubscription(
                    subscription.getKey(),
                    record.getElementInstanceKey(),
                    record.getMessageName(),
                    record.getTenantId()),
                ActorClock.currentTimeMillis());
          }
        });
  }

  @Override
  public void put(final long key, final ProcessMessageSubscriptionRecord record) {
    wrapSubscriptionKeys(
        key, record.getElementInstanceKey(), record.getMessageNameBuffer(), record.getTenantId());

    processMessageSubscription.reset();
    processMessageSubscription.setKey(key).setRecord(record);

    subscriptionColumnFamily.insert(
        elementKeyMessageNameAndSubscriptionKey, processMessageSubscription);

    transientState.add(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()),
        ActorClock.currentTimeMillis());
  }

  @Override
  public void updateToOpeningState(final long key, final ProcessMessageSubscriptionRecord record) {
    update(key, record, s -> s.setRecord(record).setOpening());
    transientState.update(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()),
        ActorClock.currentTimeMillis());
  }

  @Override
  public void updateToOpenedState(final long key, final ProcessMessageSubscriptionRecord record) {
    update(key, record, s -> s.setRecord(record).setOpened());
    transientState.remove(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()));
  }

  @Override
  public void updateToClosingState(final long key, final ProcessMessageSubscriptionRecord record) {
    update(key, record, s -> s.setRecord(record).setClosing());
    transientState.update(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()),
        ActorClock.currentTimeMillis());
  }

  @Override
  public boolean remove(
      final long key,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final String tenantId) {
    final ProcessMessageSubscription subscription =
        getSubscription(key, elementInstanceKey, messageName, tenantId);
    final boolean found = subscription != null;
    if (found) {
      remove(key, subscription);
    }
    return found;
  }

  @Override
  public ProcessMessageSubscription getSubscription(
      final long key,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final String tenantId) {
    wrapSubscriptionKeys(key, elementInstanceKey, messageName, tenantId);

    return subscriptionColumnFamily.get(elementKeyMessageNameAndSubscriptionKey);
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
      final long elementInstanceKey, final DirectBuffer messageName, final String tenantId) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
    tenantIdKey.wrapString(tenantId);

    final AtomicBoolean found = new AtomicBoolean(false);
    subscriptionColumnFamily.whileEqualPrefix(
        elementKeyAndMessageName,
        (compositeKey, subscription) -> {
          found.set(true);
        });

    return found.get();
  }

  @Override
  public void visitPending(final long deadline, final ProcessMessageSubscriptionVisitor visitor) {

    for (final var pendingSubscription : transientState.entriesBefore(deadline)) {
      final var subscription =
          getSubscription(
              pendingSubscription.subscriptionKey(),
              pendingSubscription.elementInstanceKey(),
              BufferUtil.wrapString(pendingSubscription.messageName()),
              pendingSubscription.tenantId());
      if (subscription == null) {
        LOG.warn(
            "Expected to find subscription with key {} messageName {} tenantId: {}, but no subscription found",
            pendingSubscription.elementInstanceKey(),
            pendingSubscription.messageName(),
            pendingSubscription.tenantId());
        continue;
      }
      visitor.visit(subscription);
    }
  }

  @Override
  public void onSent(
      final long key, final ProcessMessageSubscriptionRecord record, final long timestampMs) {
    transientState.update(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()),
        timestampMs);
  }

  private void update(
      final long key,
      final ProcessMessageSubscriptionRecord record,
      final Consumer<ProcessMessageSubscription> modifier) {
    final ProcessMessageSubscription subscription =
        getSubscription(
            key,
            record.getElementInstanceKey(),
            record.getMessageNameBuffer(),
            record.getTenantId());
    if (subscription == null) {
      return;
    }

    update(key, subscription, modifier);
  }

  private void update(
      final long key,
      final ProcessMessageSubscription subscription,
      final Consumer<ProcessMessageSubscription> modifier) {
    modifier.accept(subscription);

    final var record = subscription.getRecord();
    wrapSubscriptionKeys(
        key, record.getElementInstanceKey(), record.getMessageNameBuffer(), record.getTenantId());
    subscriptionColumnFamily.update(elementKeyMessageNameAndSubscriptionKey, subscription);
  }

  private void remove(final long key, final ProcessMessageSubscription subscription) {
    final var record = subscription.getRecord();
    wrapSubscriptionKeys(
        key, record.getElementInstanceKey(), record.getMessageNameBuffer(), record.getTenantId());

    subscriptionColumnFamily.deleteExisting(elementKeyMessageNameAndSubscriptionKey);

    transientState.remove(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()));
  }

  private void wrapSubscriptionKeys(
      final long key,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final String tenantId) {
    subscriptionKey.wrapLong(key);
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
    tenantIdKey.wrapString(tenantId);
  }
}
