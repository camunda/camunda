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
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DbMessageSubscriptionState
    implements MutableMessageSubscriptionState,
        PendingMessageSubscriptionState,
        StreamProcessorLifecycleAware {

  // (elementInstanceKey, messageName) => MessageSubscription
  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final MessageSubscription messageSubscription;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, MessageSubscription>
      subscriptionColumnFamily;

  // (tenant aware messageName, correlationKey, elementInstanceKey) => \0
  private final DbString tenantIdKey;
  private final DbString correlationKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareNameAndCorrelationKey;
  private final DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>
      tenantAwareNameCorrelationAndElementInstanceKey;
  private final ColumnFamily<
          DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbNil>
      messageNameAndCorrelationKeyColumnFamily;

  private final TransientPendingSubscriptionState transientState;

  public DbMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final TransientPendingSubscriptionState transientState) {

    elementInstanceKey = new DbLong();
    messageName = new DbString();
    messageSubscription = new MessageSubscription();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyAndMessageName,
            messageSubscription);

    tenantIdKey = new DbString();
    correlationKey = new DbString();
    tenantAwareNameAndCorrelationKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(messageName, correlationKey), PlacementType.PREFIX);
    tenantAwareNameCorrelationAndElementInstanceKey =
        new DbCompositeKey<>(tenantAwareNameAndCorrelationKey, elementInstanceKey);
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
                    elementInstanceKey.getValue(), messageName.toString(), tenantIdKey.toString()),
                ActorClock.currentTimeMillis());
          }
        });
  }

  @Override
  public MessageSubscription get(final long elementInstanceKey, final DirectBuffer messageName) {
    this.messageName.wrapBuffer(messageName);
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    return subscriptionColumnFamily.get(elementKeyAndMessageName);
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

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  @Override
  public void put(final long key, final MessageSubscriptionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    elementInstanceKey.wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());

    messageSubscription.setKey(key).setRecord(record).setCorrelating(false);

    subscriptionColumnFamily.insert(elementKeyAndMessageName, messageSubscription);

    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.insert(
        tenantAwareNameCorrelationAndElementInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void updateToCorrelatingState(final MessageSubscriptionRecord record) {
    final var messageKey = record.getMessageKey();
    var messageVariables = record.getVariablesBuffer();
    if (record == messageSubscription.getRecord()) {
      // copy the buffer before loading the subscription to avoid that it is overridden
      messageVariables = BufferUtil.cloneBuffer(record.getVariablesBuffer());
    }

    final var subscription = get(record.getElementInstanceKey(), record.getMessageNameBuffer());
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
            subscription.getRecord().getElementInstanceKey(),
            subscription.getRecord().getMessageName(),
            subscription.getRecord().getTenantId()),
        ActorClock.currentTimeMillis());
  }

  @Override
  public void updateToCorrelatedState(final MessageSubscription subscription) {
    updateCorrelatingFlag(subscription, false);
    final var record = subscription.getRecord();
    transientState.remove(
        new PendingSubscription(
            record.getElementInstanceKey(), record.getMessageName(), record.getTenantId()));
  }

  @Override
  public boolean remove(final long elementInstanceKey, final DirectBuffer messageName) {
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

  @Override
  public void remove(final MessageSubscription subscription) {
    subscriptionColumnFamily.deleteExisting(elementKeyAndMessageName);

    final var record = subscription.getRecord();
    tenantIdKey.wrapString(record.getTenantId());
    messageName.wrapBuffer(record.getMessageNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.deleteExisting(
        tenantAwareNameCorrelationAndElementInstanceKey);

    transientState.remove(
        new PendingSubscription(
            elementInstanceKey.getValue(), messageName.toString(), tenantIdKey.toString()));
  }

  private void updateCorrelatingFlag(
      final MessageSubscription subscription, final boolean correlating) {
    final var record = subscription.getRecord();
    elementInstanceKey.wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());

    subscription.setCorrelating(correlating);
    subscriptionColumnFamily.update(elementKeyAndMessageName, subscription);
  }

  private Boolean visitMessageSubscription(
      final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName,
      final MessageSubscriptionVisitor visitor) {
    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    if (messageSubscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find subscription with key %d and %s, but no subscription found",
              elementKeyAndMessageName.first().getValue(), elementKeyAndMessageName.second()));
    }
    return visitor.visit(messageSubscription);
  }

  @Override
  public void visitPending(final long deadline, final MessageSubscriptionVisitor visitor) {
    for (final var pendingSubscription : transientState.entriesBefore(deadline)) {
      final var elementInstanceKey = pendingSubscription.elementInstanceKey();
      final var messageName = pendingSubscription.messageName();
      final var subscription = get(elementInstanceKey, BufferUtil.wrapString(messageName));

      if (subscription == null) {
        // This case can occur while a scheduled job is running asynchronously
        // and the stream processor removes one of the returned subscriptions from the state.
        LOG.warn(
            "Expected to find a subscription with key {} and message name {}, but none found. The state is inconsistent.",
            elementInstanceKey,
            messageName);
      } else {
        visitor.visit(subscription);
      }
    }
  }

  @Override
  public void onSent(
      final long elementInstanceKey,
      final String messageName,
      final String tenantId,
      final long timestampMs) {
    transientState.update(
        new PendingSubscription(elementInstanceKey, messageName, tenantId), timestampMs);
  }
}
