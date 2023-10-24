/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.signal;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class DbSignalSubscriptionState implements MutableSignalSubscriptionState {

  private final DbString signalName;
  // processDefinitionKey or elementInstanceKey
  private final DbLong subscriptionKey;

  // [[tenant_id, signalName], subscriptionKey] => SignalSubscription
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbString> tenantAwareSignalName;
  private final DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>
      tenantAwareSignalNameAndSubscriptionKey;
  private final ColumnFamily<DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>, SignalSubscription>
      signalNameAndSubscriptionKeyColumnFamily;
  private final SignalSubscription signalSubscription = new SignalSubscription();

  // [subscriptionKey, [tenantId, signalName]] => \0  : to find existing subscriptions of a process
  // or element
  private final DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>
      tenantAwareSubscriptionKeyAndSignalName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, DbNil>
      subscriptionKeyAndSignalNameColumnFamily;

  public DbSignalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    signalName = new DbString();
    subscriptionKey = new DbLong();
    tenantIdKey = new DbString();
    tenantAwareSignalName = new DbTenantAwareKey<>(tenantIdKey, signalName, PlacementType.PREFIX);
    tenantAwareSignalNameAndSubscriptionKey =
        new DbCompositeKey<>(tenantAwareSignalName, subscriptionKey);
    signalNameAndSubscriptionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            tenantAwareSignalNameAndSubscriptionKey,
            signalSubscription);

    tenantAwareSubscriptionKeyAndSignalName =
        new DbCompositeKey<>(subscriptionKey, tenantAwareSignalName);
    subscriptionKeyAndSignalNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
            transactionContext,
            tenantAwareSubscriptionKeyAndSignalName,
            DbNil.INSTANCE);
  }

  @Override
  public void put(final long key, final SignalSubscriptionRecord subscription) {
    signalSubscription.setKey(key).setRecord(subscription);

    wrapSubscriptionKeys(subscription);

    signalNameAndSubscriptionKeyColumnFamily.upsert(
        tenantAwareSignalNameAndSubscriptionKey, signalSubscription);
    subscriptionKeyAndSignalNameColumnFamily.upsert(
        tenantAwareSubscriptionKeyAndSignalName, DbNil.INSTANCE);
  }

  @Override
  public void remove(
      final long subscriptionKey, final DirectBuffer signalName, final String tenantId) {
    wrapSubscriptionKeys(subscriptionKey, signalName, tenantId);

    signalNameAndSubscriptionKeyColumnFamily.deleteExisting(
        tenantAwareSignalNameAndSubscriptionKey);
    subscriptionKeyAndSignalNameColumnFamily.deleteExisting(
        tenantAwareSubscriptionKeyAndSignalName);
  }

  @Override
  public boolean exists(final SignalSubscriptionRecord subscription) {
    wrapSubscriptionKeys(subscription);
    return signalNameAndSubscriptionKeyColumnFamily.exists(tenantAwareSignalNameAndSubscriptionKey);
  }

  @Override
  public void visitBySignalName(
      final DirectBuffer signalName,
      final String tenantId,
      final SignalSubscriptionVisitor visitor) {
    tenantIdKey.wrapString(tenantId);
    this.signalName.wrapBuffer(signalName);
    signalNameAndSubscriptionKeyColumnFamily.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, this.signalName),
        (key, value) -> {
          visitor.visit(value);
        });
  }

  @Override
  public void visitStartEventSubscriptionsByProcessDefinitionKey(
      final long processDefinitionKey, final SignalSubscriptionVisitor visitor) {
    subscriptionKey.wrapLong(processDefinitionKey);
    visitSubscriptions(visitor);
  }

  @Override
  public void visitByElementInstanceKey(
      final long elementInstanceKey, final SignalSubscriptionVisitor visitor) {
    subscriptionKey.wrapLong(elementInstanceKey);
    visitSubscriptions(visitor);
  }

  private void visitSubscriptions(final SignalSubscriptionVisitor visitor) {
    subscriptionKeyAndSignalNameColumnFamily.whileEqualPrefix(
        subscriptionKey,
        (key, value) -> {
          // the signalName and tenantIdKey are wrapped on look-up in the background
          final var subscription =
              signalNameAndSubscriptionKeyColumnFamily.get(tenantAwareSignalNameAndSubscriptionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }

  private void wrapSubscriptionKeys(final SignalSubscriptionRecord subscription) {
    final var key = subscription.getSubscriptionKey();
    wrapSubscriptionKeys(key, subscription.getSignalNameBuffer(), subscription.getTenantId());
  }

  private void wrapSubscriptionKeys(
      final long key, final DirectBuffer signalName, final String tenantId) {
    subscriptionKey.wrapLong(key);
    this.signalName.wrapBuffer(signalName);
    tenantIdKey.wrapString(tenantId);
  }
}
