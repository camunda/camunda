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
  private final DbLong processInstanceKey;
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
  private final ColumnFamily<DbLong, DbInt> processInstanceKeyCountColumnFamily;

  private final ColumnFamily<DbTenantAwareKey<DbLong>, DbNil> tenantIdColumnFamily;

  public DbConditionalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    subscriptionKey = new DbLong();
    scopeKey = new DbLong();
    processDefinitionKey = new DbLong();
    processInstanceKey = new DbLong();
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

    processInstanceKeyCountColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_PROCESS_INSTANCE_COUNT,
            transactionContext,
            processInstanceKey,
            subscriptionCount);

    tenantIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_TENANT_ID,
            transactionContext,
            tenantAwareSubscriptionKey,
            DbNil.INSTANCE);
  }

  @Override
  public void put(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    subscriptionKeyColumnFamily.insert(tenantAwareSubscriptionKey, conditionalSubscription);
    scopeKeyColumnFamily.insert(scopeKeyAndTenantAwareSubscriptionKey, DbNil.INSTANCE);
    incrementProcessInstanceKeySubscriptionCount(subscription);
  }

  @Override
  public void migrate(final long key, final ConditionalSubscriptionRecord subscription) {
    conditionalSubscription.setKey(key).setRecord(subscription);
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());

    subscriptionKeyColumnFamily.update(tenantAwareSubscriptionKey, conditionalSubscription);
    // scope key and process instance key do not change during migration
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
    tenantIdColumnFamily.insert(tenantAwareSubscriptionKey, DbNil.INSTANCE);
  }

  @Override
  public void delete(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    scopeKey.wrapLong(subscription.getScopeKey());

    subscriptionKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    scopeKeyColumnFamily.deleteExisting(scopeKeyAndTenantAwareSubscriptionKey);
    decrementProcessInstanceKeySubscriptionCount(subscription);
  }

  @Override
  public void deleteStart(final long key, final ConditionalSubscriptionRecord subscription) {
    subscriptionKey.wrapLong(key);
    tenantIdKey.wrapString(subscription.getTenantId());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    subscriptionKeyColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
    processDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndTenantAwareSubscriptionKey);
    tenantIdColumnFamily.deleteExisting(tenantAwareSubscriptionKey);
  }

  @Override
  public boolean exists(final String tenantId, final long subscriptionKey) {
    this.subscriptionKey.wrapLong(subscriptionKey);
    tenantIdKey.wrapString(tenantId);

    return subscriptionKeyColumnFamily.exists(tenantAwareSubscriptionKey);
  }

  @Override
  public boolean exists(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);

    return processInstanceKeyCountColumnFamily.exists(this.processInstanceKey);
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

  @Override
  public void visitStartEventSubscriptionsByTenantId(
      final String tenantId, final ConditionalSubscriptionVisitor visitor) {

    tenantIdKey.wrapString(tenantId);

    tenantIdColumnFamily.whileEqualPrefix(
        tenantIdKey,
        (key, nil) -> {
          subscriptionKey.wrapLong(key.wrappedKey().getValue());
          final var subscription =
              subscriptionKeyColumnFamily.get(
                  tenantAwareSubscriptionKey, ConditionalSubscription::new);
          if (subscription != null) {
            return visitor.visit(subscription);
          }
          return true;
        });
  }

  private void incrementProcessInstanceKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processInstanceKey.wrapLong(subscription.getProcessInstanceKey());

    final DbInt count = processInstanceKeyCountColumnFamily.get(processInstanceKey);
    final var newCount = count == null ? 1 : count.getValue() + 1;

    subscriptionCount.wrapInt(newCount);
    processInstanceKeyCountColumnFamily.upsert(processInstanceKey, subscriptionCount);
  }

  private void decrementProcessInstanceKeySubscriptionCount(
      final ConditionalSubscriptionRecord subscription) {
    processInstanceKey.wrapLong(subscription.getProcessInstanceKey());
    final DbInt count = processInstanceKeyCountColumnFamily.get(processInstanceKey);

    if (count == null) {
      throw new IllegalStateException(
          "Tried to decrement conditional subscription count for process instance key "
              + subscription.getProcessInstanceKey()
              + " but no count was found.");
    }

    final int newCount = count.getValue() - 1;
    if (newCount > 0) {
      subscriptionCount.wrapInt(newCount);
      processInstanceKeyCountColumnFamily.update(processInstanceKey, subscriptionCount);
    } else {
      processInstanceKeyCountColumnFamily.deleteExisting(processInstanceKey);
    }
  }
}
