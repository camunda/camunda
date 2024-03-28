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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DbMessageSubscriptionState
    implements MutableMessageSubscriptionState,
        PendingMessageSubscriptionState,
        StreamProcessorLifecycleAware {

  // (elementInstanceKey, messageName) => MessageSubscription
  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private final DbLong subscriptionKey;
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final MessageSubscription messageSubscription;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbLong>
      elementKeyMessageNameAndSubscriptionKey;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbLong>, MessageSubscription>
      subscriptionColumnFamily;

  // (tenant aware messageName, correlationKey, elementInstanceKey) => \0
  private final DbString tenantIdKey;
  private final DbString correlationKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareNameAndCorrelationKey;
  private final DbCompositeKey<
          DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbLong>
      tenantAwareNameCorrelationAndElementInstanceKey;
  private final DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>
      tenantAwareNameCorrelationAndElementInstanceKeySubKey;
  private final ColumnFamily<
          DbCompositeKey<
              DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbLong>,
          DbNil>
      messageNameAndCorrelationKeyColumnFamily;

  private final TransientPendingSubscriptionState transientState;

  public DbMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final TransientPendingSubscriptionState transientState) {

    subscriptionKey = new DbLong();
    elementInstanceKey = new DbLong();
    messageName = new DbString();
    messageSubscription = new MessageSubscription();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    elementKeyMessageNameAndSubscriptionKey =
        new DbCompositeKey<>(elementKeyAndMessageName, subscriptionKey);
    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyMessageNameAndSubscriptionKey,
            messageSubscription);

    tenantIdKey = new DbString();
    correlationKey = new DbString();
    tenantAwareNameAndCorrelationKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(messageName, correlationKey), PlacementType.PREFIX);
    tenantAwareNameCorrelationAndElementInstanceKeySubKey =
        new DbCompositeKey<>(tenantAwareNameAndCorrelationKey, subscriptionKey);
    tenantAwareNameCorrelationAndElementInstanceKey =
        new DbCompositeKey<>(
            tenantAwareNameCorrelationAndElementInstanceKeySubKey, elementInstanceKey);
    messageNameAndCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
            transactionContext,
            tenantAwareNameCorrelationAndElementInstanceKey,
            DbNil.INSTANCE);
    this.transientState = transientState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    subscriptionColumnFamily.forEach(
        subscription -> {
          if (subscription.isCorrelating()) {
            transientState.add(
                new PendingSubscription(
                    subscription.getKey(),
                    elementInstanceKey.getValue(),
                    messageName.toString(),
                    tenantIdKey.toString()),
                ActorClock.currentTimeMillis());
          }
        });
  }

  @Override
  public MessageSubscription get(
      final long key, final long elementInstanceKey, final DirectBuffer messageName) {
    this.messageName.wrapBuffer(messageName);
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    subscriptionKey.wrapLong(key);
    return subscriptionColumnFamily.get(elementKeyMessageNameAndSubscriptionKey);
  }

  @Override
  public void visitSubscriptions(
      final String tenantId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final MessageSubscriptionVisitor visitor) {

    tenantIdKey.wrapString(tenantId);
    this.messageName.wrapBuffer(messageName);
    this.correlationKey.wrapBuffer(correlationKey);

    messageNameAndCorrelationKeyColumnFamily.whileEqualPrefix(
        tenantAwareNameAndCorrelationKey,
        (compositeKey, nil) -> {
          return visitMessageSubscription(elementKeyAndMessageName, visitor);
        });
  }

  @Override
  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    final AtomicBoolean found = new AtomicBoolean(false);

    subscriptionColumnFamily.whileEqualPrefix(
        elementKeyAndMessageName,
        (compositeKey, messageSubscription) -> {
          found.set(true);
        });

    return found.get();
  }

  @Override
  public void put(final long key, final MessageSubscriptionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    elementInstanceKey.wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());
    subscriptionKey.wrapLong(key);

    messageSubscription.setKey(key).setRecord(record).setCorrelating(false);

    subscriptionColumnFamily.insert(elementKeyMessageNameAndSubscriptionKey, messageSubscription);

    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.insert(
        tenantAwareNameCorrelationAndElementInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void updateToCorrelatingState(final long key, final MessageSubscriptionRecord record) {
    final var messageKey = record.getMessageKey();
    var messageVariables = record.getVariablesBuffer();
    if (record == messageSubscription.getRecord()) {
      // copy the buffer before loading the subscription to avoid that it is overridden
      messageVariables = BufferUtil.cloneBuffer(record.getVariablesBuffer());
    }

    final var subscription =
        get(key, record.getElementInstanceKey(), record.getMessageNameBuffer());
    if (subscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected subscription but not found. [element-instance-key: %d, message-name: %s]",
              record.getElementInstanceKey(), record.getMessageName()));
    }

    // update the message key and the variables
    subscription.getRecord().setMessageKey(messageKey).setVariables(messageVariables);

    updateCorrelatingFlag(subscription, true);

    transientState.update(
        new PendingSubscription(
            key,
            subscription.getRecord().getElementInstanceKey(),
            subscription.getRecord().getMessageName(),
            subscription.getRecord().getTenantId()),
        ActorClock.currentTimeMillis());
  }

  @Override
  public void updateToCorrelatedState(final long key, final MessageSubscription subscription) {
    updateCorrelatingFlag(subscription, false);
    final var record = subscription.getRecord();
    transientState.remove(
        new PendingSubscription(
            key, record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()));
  }

  @Override
  public boolean remove(
      final long key, final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
    subscriptionKey.wrapLong(key);

    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyMessageNameAndSubscriptionKey);

    final boolean found = messageSubscription != null;
    if (found) {
      remove(messageSubscription);
    }
    return found;
  }

  @Override
  public void remove(final MessageSubscription subscription) {
    subscriptionColumnFamily.deleteExisting(elementKeyMessageNameAndSubscriptionKey);

    final var record = subscription.getRecord();
    tenantIdKey.wrapString(record.getTenantId());
    messageName.wrapBuffer(record.getMessageNameBuffer());
    subscriptionKey.wrapLong(subscription.getKey());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.deleteExisting(
        tenantAwareNameCorrelationAndElementInstanceKey);

    transientState.remove(
        new PendingSubscription(
            subscriptionKey.getValue(),
            elementInstanceKey.getValue(),
            messageName.toString(),
            tenantIdKey.toString()));
  }

  private void updateCorrelatingFlag(
      final MessageSubscription subscription, final boolean correlating) {
    final var record = subscription.getRecord();
    elementInstanceKey.wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());
    subscriptionKey.wrapLong(subscription.getKey());

    subscription.setCorrelating(correlating);
    subscriptionColumnFamily.update(elementKeyMessageNameAndSubscriptionKey, subscription);
  }

  private Boolean visitMessageSubscription(
      final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName,
      final MessageSubscriptionVisitor visitor) {

    final AtomicInteger visited = new AtomicInteger(0);
    subscriptionColumnFamily.whileEqualPrefix(
        elementKeyAndMessageName,
        (compositeKey, messageSubscription) -> {
          visitor.visit(messageSubscription);
          visited.incrementAndGet();
        });

    if (visited.get() == 0) {
      throw new IllegalStateException(
          String.format(
              "Expected to find subscription with key %d and %s, but no subscription found",
              elementKeyAndMessageName.first().getValue(), elementKeyAndMessageName.second()));
    }
    return visited.get() > 0;
  }

  @Override
  public void visitPending(final long deadline, final MessageSubscriptionVisitor visitor) {
    for (final var pendingSubscription : transientState.entriesBefore(deadline)) {
      final var subscription =
          get(
              pendingSubscription.subscriptionKey(),
              pendingSubscription.elementInstanceKey(),
              BufferUtil.wrapString(pendingSubscription.messageName()));

      if (subscription == null) {
        // This case can occur while a scheduled job is running asynchronously
        // and the stream processor removes one of the returned subscriptions from the state.
        LOG.warn(
            "Expected to find a subscription with key {} and message name {}, but none found. The state is inconsistent.",
            pendingSubscription.elementInstanceKey(),
            pendingSubscription.messageName());
      } else {
        visitor.visit(subscription);
      }
    }
  }

  @Override
  public void onSent(
      final long key,
      final long elementInstanceKey,
      final String messageName,
      final String tenantId,
      final long timestampMs) {
    transientState.update(
        new PendingSubscription(key, elementInstanceKey, messageName, tenantId), timestampMs);
  }
}
