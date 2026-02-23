/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hubmetrics;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableHubMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

public class DbHubMetricsState implements MutableHubMetricsState {

  private final DbString tenantIdKey;
  private final DbString processId;
  private final DbInt processVersion;
  private final DbCompositeKey<DbString, DbInt> idAndVersionKey;
  private final ProcessMetricsValue processMetricsValue;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbInt>> tenantAwareProcessIdAndVersionKey;
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>, ProcessMetricsValue>
      processByTenantIDAndVersionMetricsColumnFamily;

  private final DbString elementId;
  private final DbCompositeKey<DbInt, DbString> versionAndElementIdKey;
  private final DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>
      idAndVersionAndElementIdKey;
  private final ElementMetricsValue elementMetricsValue;

  private final DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>>
      tenantAwareProcessIdVersionAndElementIdKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>>,
          ElementMetricsValue>
      processByTenantIDVersionAndElementMetricsColumnFamily;

  public DbHubMetricsState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantIdKey = new DbString();
    processId = new DbString();
    processVersion = new DbInt();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processMetricsValue = new ProcessMetricsValue();

    processByTenantIDAndVersionMetricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_METRICS,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            processMetricsValue);

    elementId = new DbString();
    versionAndElementIdKey = new DbCompositeKey<>(processVersion, elementId);
    idAndVersionAndElementIdKey = new DbCompositeKey<>(processId, versionAndElementIdKey);
    tenantAwareProcessIdVersionAndElementIdKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionAndElementIdKey, PlacementType.PREFIX);
    elementMetricsValue = new ElementMetricsValue();

    processByTenantIDVersionAndElementMetricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_METRICS,
            transactionContext,
            tenantAwareProcessIdVersionAndElementIdKey,
            elementMetricsValue);
  }

  @Override
  public void updateOnProcessInstanceCreated(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());

    final var value =
        processByTenantIDAndVersionMetricsColumnFamily.get(tenantAwareProcessIdAndVersionKey);
    if (value != null) {
      value.incrementAbsolute();
      value.incrementCreated();
      processByTenantIDAndVersionMetricsColumnFamily.update(
          tenantAwareProcessIdAndVersionKey, value);
    } else {
      final var v = new ProcessMetricsValue();
      v.incrementAbsolute();
      v.incrementCreated();
      processByTenantIDAndVersionMetricsColumnFamily.insert(tenantAwareProcessIdAndVersionKey, v);
    }
  }

  @Override
  public void updateOnProcessInstanceCompleted(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());

    final var value =
        processByTenantIDAndVersionMetricsColumnFamily.get(tenantAwareProcessIdAndVersionKey);
    if (value != null) {
      value.decrementAbsolute();
      value.incrementCompleted();
      processByTenantIDAndVersionMetricsColumnFamily.update(
          tenantAwareProcessIdAndVersionKey, value);
    } else {
      final var v = new ProcessMetricsValue();
      v.decrementAbsolute();
      v.incrementCompleted();
      processByTenantIDAndVersionMetricsColumnFamily.insert(tenantAwareProcessIdAndVersionKey, v);
    }
  }

  @Override
  public void updateOnElementCreated(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());
    elementId.wrapBuffer(record.getElementIdBuffer());

    final var value =
        processByTenantIDVersionAndElementMetricsColumnFamily.get(
            tenantAwareProcessIdVersionAndElementIdKey);
    if (value != null) {
      value.incrementCreated();
      processByTenantIDVersionAndElementMetricsColumnFamily.update(
          tenantAwareProcessIdVersionAndElementIdKey, value);

    } else {
      final var v = new ElementMetricsValue();
      v.incrementCreated();
      processByTenantIDVersionAndElementMetricsColumnFamily.insert(
          tenantAwareProcessIdVersionAndElementIdKey, v);
    }
  }

  @Override
  public void updateOnElementCompleted(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());
    elementId.wrapBuffer(record.getElementIdBuffer());

    final var value =
        processByTenantIDVersionAndElementMetricsColumnFamily.get(
            tenantAwareProcessIdVersionAndElementIdKey);
    if (value != null) {
      value.incrementCompleted();
      processByTenantIDVersionAndElementMetricsColumnFamily.update(
          tenantAwareProcessIdVersionAndElementIdKey, value);

    } else {
      final var v = new ElementMetricsValue();
      v.incrementCompleted();
      processByTenantIDVersionAndElementMetricsColumnFamily.insert(
          tenantAwareProcessIdVersionAndElementIdKey, v);
    }
  }

  @Override
  public void reset() {
    processByTenantIDAndVersionMetricsColumnFamily.whileTrue(
        (k, v) -> {
          v.setCompleted(0);
          v.setCreated(0);
          processByTenantIDAndVersionMetricsColumnFamily.upsert(k, v);
          return true;
        });
    processByTenantIDVersionAndElementMetricsColumnFamily.whileTrue(
        (k, v) -> {
          processByTenantIDVersionAndElementMetricsColumnFamily.deleteExisting(k);
          return true;
        });
  }
}
