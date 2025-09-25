/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.compensation;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.common.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DbCompensationSubscriptionState implements MutableCompensationSubscriptionState {

  private final DbLong processInstanceKey;
  private final DbLong recordKey;
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessInstanceKey;
  private final DbCompositeKey<DbTenantAwareKey<DbLong>, DbLong>
      tenantAwareProcessInstanceKeyCompensableActivityId;
  private final ColumnFamily<
          DbCompositeKey<DbTenantAwareKey<DbLong>, DbLong>, CompensationSubscription>
      compensationSubscriptionColumnFamily;
  private final CompensationSubscription compensationSubscription = new CompensationSubscription();

  public DbCompensationSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processInstanceKey = new DbLong();
    recordKey = new DbLong();
    tenantIdKey = new DbString();
    tenantAwareProcessInstanceKey =
        new DbTenantAwareKey<>(tenantIdKey, processInstanceKey, PlacementType.PREFIX);
    tenantAwareProcessInstanceKeyCompensableActivityId =
        new DbCompositeKey<>(tenantAwareProcessInstanceKey, recordKey);
    compensationSubscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.COMPENSATION_SUBSCRIPTION,
            transactionContext,
            tenantAwareProcessInstanceKeyCompensableActivityId,
            compensationSubscription);
  }

  @Override
  public CompensationSubscription get(
      final String tenantId, final long processInstanceKey, final long recordKey) {
    wrapCompensationKeys(processInstanceKey, recordKey, tenantId);
    return compensationSubscriptionColumnFamily.get(
        tenantAwareProcessInstanceKeyCompensableActivityId, CompensationSubscription::new);
  }

  @Override
  public List<CompensationSubscription> findSubscriptionsByProcessInstanceKey(
      final String tenantId, final long piKey) {
    tenantIdKey.wrapString(tenantId);
    processInstanceKey.wrapLong(piKey);

    final List<CompensationSubscription> subscriptions = new ArrayList<>();
    compensationSubscriptionColumnFamily.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, processInstanceKey),
        ((key, value) -> {
          subscriptions.add(value.copy());
        }));
    return subscriptions;
  }

  @Override
  public Optional<CompensationSubscription> findSubscriptionByCompensationHandlerId(
      final String tenantId, final long piKey, final String compensationHandlerId) {
    tenantIdKey.wrapString(tenantId);
    processInstanceKey.wrapLong(piKey);

    final List<CompensationSubscription> compensationSubscription = new ArrayList<>();
    compensationSubscriptionColumnFamily.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, processInstanceKey),
        ((key, value) -> {
          if (value.getRecord().getCompensationHandlerId().equals(compensationHandlerId)) {
            compensationSubscription.add(value.copy());
          }
        }));

    return compensationSubscription.stream().findFirst();
  }

  @Override
  public List<CompensationSubscription> findSubscriptionsByThrowEventInstanceKey(
      final String tenantId, final long piKey, final long throwEventInstanceKey) {
    tenantIdKey.wrapString(tenantId);
    processInstanceKey.wrapLong(piKey);

    final List<CompensationSubscription> compensations = new ArrayList<>();
    compensationSubscriptionColumnFamily.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, processInstanceKey),
        ((key, value) -> {
          if (value.getRecord().getThrowEventInstanceKey() == throwEventInstanceKey) {
            compensations.add(value.copy());
          }
        }));
    return compensations;
  }

  @Override
  public void put(final long key, final CompensationSubscriptionRecord compensation) {
    compensationSubscription.setKey(key).setRecord(compensation);

    wrapCompensationKeys(compensation.getProcessInstanceKey(), key, compensation.getTenantId());

    compensationSubscriptionColumnFamily.upsert(
        tenantAwareProcessInstanceKeyCompensableActivityId, compensationSubscription);
  }

  @Override
  public void update(final long key, final CompensationSubscriptionRecord compensation) {
    compensationSubscription.setKey(key).setRecord(compensation);
    wrapCompensationKeys(compensation.getProcessInstanceKey(), key, compensation.getTenantId());
    compensationSubscriptionColumnFamily.update(
        tenantAwareProcessInstanceKeyCompensableActivityId, compensationSubscription);
  }

  @Override
  public void delete(final String tenantId, final long processInstanceKey, final long recordKey) {
    wrapCompensationKeys(processInstanceKey, recordKey, tenantId);

    compensationSubscriptionColumnFamily.deleteExisting(
        tenantAwareProcessInstanceKeyCompensableActivityId);
  }

  private void wrapCompensationKeys(
      final long processInstance, final long key, final String tenantId) {
    processInstanceKey.wrapLong(processInstance);
    recordKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
  }
}
