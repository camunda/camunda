/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.conditional;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.engine.state.mutable.MutableConditionSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.condition.ConditionSubscriptionRecord;
import java.util.ArrayList;
import java.util.List;

public class DbConditionSubscriptionState implements MutableConditionSubscriptionState {

  private final ConditionSubscription conditionSubscription = new ConditionSubscription();
  private final DbLong subscriptionKey;
  private final DbLong scopeKey;
  private final DbString tenantIdKey;

  private final DbTenantAwareKey<DbLong> tenantAwareSubscriptionKey;
  private final DbCompositeKey<DbLong, DbLong> scopeKeyAndSubscriptionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>>
      tenantAwareScopeKeyAndSubscriptionKey;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, ConditionSubscription>
      conditionKeyColumnFamily;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>>, ConditionSubscription>
      scopeKeyColumnFamily;

  public DbConditionSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    subscriptionKey = new DbLong();
    scopeKey = new DbLong();
    tenantIdKey = new DbString();
    tenantAwareSubscriptionKey =
        new DbTenantAwareKey<>(tenantIdKey, subscriptionKey, DbTenantAwareKey.PlacementType.PREFIX);
    scopeKeyAndSubscriptionKey = new DbCompositeKey<>(scopeKey, subscriptionKey);
    tenantAwareScopeKeyAndSubscriptionKey =
        new DbTenantAwareKey<>(
            tenantIdKey, scopeKeyAndSubscriptionKey, DbTenantAwareKey.PlacementType.PREFIX);
    conditionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITION_SUBSCRIPTION_BY_CONDITION_KEY,
            transactionContext,
            tenantAwareSubscriptionKey,
            conditionSubscription);
    scopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITION_SUBSCRIPTION_BY_SCOPE_KEY,
            transactionContext,
            tenantAwareScopeKeyAndSubscriptionKey,
            conditionSubscription);
  }

  @Override
  public void put(final long key, final ConditionSubscriptionRecord subscription) {
    conditionSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    conditionKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionSubscription);
    scopeKeyColumnFamily.insert(tenantAwareScopeKeyAndSubscriptionKey, conditionSubscription);
  }

  @Override
  public List<ConditionSubscription> getSubscriptionsByScopeKey(
      final String tenantId, final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    tenantIdKey.wrapString(tenantId);

    final var subscriptions = new ArrayList<ConditionSubscription>();
    scopeKeyColumnFamily.whileEqualPrefix(
        new DbTenantAwareKey<>(tenantIdKey, this.scopeKey, DbTenantAwareKey.PlacementType.PREFIX),
        (ignore, subscription) -> {
          final ConditionSubscription copySubscription = new ConditionSubscription();
          copySubscription.copyFrom(subscription);
          subscriptions.add(copySubscription);
        });

    return subscriptions;
  }

  @Override
  public boolean exists(final String tenantId, final long subscriptionKey) {
    this.subscriptionKey.wrapLong(subscriptionKey);
    tenantIdKey.wrapString(tenantId);

    return conditionKeyColumnFamily.exists(tenantAwareSubscriptionKey);
  }

  @Override
  public void visitByScopeKey(final long scopeKey, final ConditionSubscriptionVisitor visitor) {}
}
