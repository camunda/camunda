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
import io.camunda.zeebe.db.impl.DbInt;
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
  private final DbLong processDefinitionKey;
  private final DbString tenantIdKey;
  private final DbInt subscriptionCount;

  private final DbTenantAwareKey<DbLong> tenantAwareSubscriptionKey;
  private final DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>
      processDefinitionKeyAndTenantAwareSubscriptionKey;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, ConditionalSubscription>
      subscriptionKeyColumnFamily;
  private final DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>
      scopeKeyAndTenantAwareSubscriptionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>, DbNil>
      scopeKeyColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbLong>>, DbNil>
      processDefinitionKeyColumnFamily;
  private final ColumnFamily<DbLong, DbInt> processDefinitionKeyCountColumnFamily;

  public DbConditionalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    subscriptionKey = new DbLong();
    scopeKey = new DbLong();
    processDefinitionKey = new DbLong();
    tenantIdKey = new DbString();
    subscriptionCount = new DbInt();
    tenantAwareSubscriptionKey =
        new DbTenantAwareKey<>(tenantIdKey, subscriptionKey, DbTenantAwareKey.PlacementType.PREFIX);
    subscriptionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SUBSCRIPTION_KEY,
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

    processDefinitionKeyAndTenantAwareSubscriptionKey =
        new DbCompositeKey<>(processDefinitionKey, tenantAwareSubscriptionKey);
    processDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_PROCESS_DEFINITION_KEY,
            transactionContext,
            processDefinitionKeyAndTenantAwareSubscriptionKey,
            DbNil.INSTANCE);
    processDefinitionKeyCountColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_PROCESS_DEFINITION_COUNT,
            transactionContext,
            processDefinitionKey,
            subscriptionCount);
  }

  @Override
  public void put(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    subscriptionKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionalSubscription);
    scopeKeyColumnFamily.insert(scopeKeyAndTenantAwareSubscriptionKey, DbNil.INSTANCE);
    incrementProcessDefinitionKeySubscriptionCount(subscription);
  }

  @Override
  public void putStart(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    subscriptionKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionalSubscription);
    processDefinitionKeyColumnFamily.insert(
        processDefinitionKeyAndTenantAwareSubscriptionKey, DbNil.INSTANCE);
  }

  @Override
  public void delete(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    subscriptionKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    scopeKeyColumnFamily.deleteExisting(scopeKeyAndTenantAwareSubscriptionKey);
    decrementProcessDefinitionKeySubscriptionCount(subscription);
  }

  @Override
  public void deleteStart(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    subscriptionKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    processDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndTenantAwareSubscriptionKey);
  }

  private void incrementProcessDefinitionKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    final DbInt dbInt = processDefinitionKeyCountColumnFamily.get(processDefinitionKey);
    if (dbInt != null) {
      subscriptionCount.wrapInt(dbInt.getValue() + 1);
    } else {
      final DbInt newCount = new DbInt();
      newCount.wrapInt(1);
      subscriptionCount.wrapInt(newCount.getValue());
    }
    processDefinitionKeyCountColumnFamily.upsert(processDefinitionKey, subscriptionCount);
  }

  private void decrementProcessDefinitionKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    final DbInt dbInt = processDefinitionKeyCountColumnFamily.get(processDefinitionKey);
    if (dbInt != null) {
      final int newCount = dbInt.getValue() - 1;
      if (newCount == 0) {
        processDefinitionKeyCountColumnFamily.deleteExisting(processDefinitionKey);
      } else {
        subscriptionCount.wrapInt(newCount);
        processDefinitionKeyCountColumnFamily.upsert(processDefinitionKey, subscriptionCount);
      }
    }
  }

  @Override
  public boolean exists(final String tenantId, final long subscriptionKey) {
    this.subscriptionKey.wrapLong(subscriptionKey);
    tenantIdKey.wrapString(tenantId);

    return subscriptionKeyColumnFamily.exists(tenantAwareSubscriptionKey);
  }

  @Override
  public boolean exists(final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    return processDefinitionKeyCountColumnFamily.exists(this.processDefinitionKey);
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
              subscriptionKeyColumnFamily.get(
                  tenantAwareSubscriptionKey, ConditionalSubscription::new);

          if (subscription != null) {
            return visitor.visit(subscription);
          }

          return true;
        });
  }

  @Override
  public void visitStartEventSubscriptionsByProcessDefinitionKey(
      final long processDefinitionKey, final ConditionalSubscriptionVisitor visitor) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    processDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, nil) -> {
          tenantIdKey.wrapString(key.second().tenantKey().toString());
          subscriptionKey.wrapLong(key.second().wrappedKey().getValue());
          final var subscription =
              subscriptionKeyColumnFamily.get(
                  tenantAwareSubscriptionKey, ConditionalSubscription::new);
          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }

  private void incrementProcessDefinitionKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    final DbInt count = processDefinitionKeyCountColumnFamily.get(processDefinitionKey);
    final var newCount = count == null ? 1 : count.getValue() + 1;

    subscriptionCount.wrapInt(newCount);
    processDefinitionKeyCountColumnFamily.upsert(processDefinitionKey, subscriptionCount);
  }

  private void decrementProcessDefinitionKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    final DbInt count = processDefinitionKeyCountColumnFamily.get(processDefinitionKey);

    if (count == null) {
      throw new IllegalStateException(
          "Tried to decrement conditional subscription count for process definition key "
              + subscription.getProcessDefinitionKey()
              + " but no count was found.");
    }

    final int newCount = count.getValue() - 1;
    if (newCount > 0) {
      subscriptionCount.wrapInt(newCount);
      processDefinitionKeyCountColumnFamily.update(processDefinitionKey, subscriptionCount);
    } else {
      processDefinitionKeyCountColumnFamily.deleteExisting(processDefinitionKey);
    }
  }
}
