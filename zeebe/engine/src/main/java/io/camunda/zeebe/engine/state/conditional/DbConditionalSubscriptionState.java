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
import io.camunda.zeebe.engine.state.mutable.MutableConditionalSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;

public class DbConditionalSubscriptionState implements MutableConditionalSubscriptionState {

  private final ConditionalSubscription conditionalSubscription = new ConditionalSubscription();
  private final DbLong subscriptionKey;
  private final DbLong scopeKey;
  private final DbString tenantIdKey;

  private final DbTenantAwareKey<DbLong> tenantAwareSubscriptionKey;
  private final DbCompositeKey<DbLong, DbLong> scopeKeyAndSubscriptionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>>
      tenantAwareScopeKeyAndSubscriptionKey;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, ConditionalSubscription>
      conditionalKeyColumnFamily;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>>, ConditionalSubscription>
      scopeKeyColumnFamily;

  public DbConditionalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    subscriptionKey = new DbLong();
    scopeKey = new DbLong();
    tenantIdKey = new DbString();
    tenantAwareSubscriptionKey =
        new DbTenantAwareKey<>(tenantIdKey, subscriptionKey, DbTenantAwareKey.PlacementType.PREFIX);
    conditionalKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_CONDITIONAL_KEY,
            transactionContext,
            tenantAwareSubscriptionKey,
            conditionalSubscription);

    scopeKeyAndSubscriptionKey = new DbCompositeKey<>(scopeKey, subscriptionKey);
    tenantAwareScopeKeyAndSubscriptionKey =
        new DbTenantAwareKey<>(
            tenantIdKey, scopeKeyAndSubscriptionKey, DbTenantAwareKey.PlacementType.PREFIX);
    scopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
            transactionContext,
            tenantAwareScopeKeyAndSubscriptionKey,
            conditionalSubscription);
  }

  @Override
  public void put(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    conditionalKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionalSubscription);
    scopeKeyColumnFamily.insert(tenantAwareScopeKeyAndSubscriptionKey, conditionalSubscription);
  }

  @Override
  public void delete(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    conditionalKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    scopeKeyColumnFamily.deleteExisting(tenantAwareScopeKeyAndSubscriptionKey);
  }

  @Override
  public boolean exists(final String tenantId, final long subscriptionKey) {
    this.subscriptionKey.wrapLong(subscriptionKey);
    tenantIdKey.wrapString(tenantId);

    return conditionalKeyColumnFamily.exists(tenantAwareSubscriptionKey);
  }

  @Override
  public void visitByScopeKey(final long scopeKey, final ConditionalSubscriptionVisitor visitor) {
    this.scopeKey.wrapLong(scopeKey);

    scopeKeyColumnFamily.whileEqualPrefix(
        new DbTenantAwareKey<>(tenantIdKey, this.scopeKey, DbTenantAwareKey.PlacementType.PREFIX),
        (ignore, subscription) -> {
          final ConditionalSubscription copySubscription = new ConditionalSubscription();
          copySubscription.copyFrom(subscription);
          visitor.visit(copySubscription);
        });
  }
}
