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
import io.camunda.zeebe.db.impl.DbNil;
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
  private final ColumnFamily<DbTenantAwareKey<DbLong>, ConditionalSubscription>
      conditionalKeyColumnFamily;
  private final DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>
      scopeKeyAndTenantAwareSubscriptionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>, DbNil>
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

    scopeKeyAndTenantAwareSubscriptionKey =
        new DbCompositeKey<>(scopeKey, tenantAwareSubscriptionKey);
    scopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
            transactionContext,
            scopeKeyAndTenantAwareSubscriptionKey,
            DbNil.INSTANCE);
  }

  @Override
  public void put(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    conditionalKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionalSubscription);
    scopeKeyColumnFamily.insert(scopeKeyAndTenantAwareSubscriptionKey, DbNil.INSTANCE);
  }

  @Override
  public void delete(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    conditionalKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    scopeKeyColumnFamily.deleteExisting(scopeKeyAndTenantAwareSubscriptionKey);
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
        this.scopeKey,
        (key, ignore) -> {
          subscriptionKey.wrapLong(key.second().wrappedKey().getValue());
          tenantIdKey.wrapBuffer(key.second().tenantKey().getBuffer());
          final var subscription =
              conditionalKeyColumnFamily.get(
                  tenantAwareSubscriptionKey, ConditionalSubscription::new);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }
}
