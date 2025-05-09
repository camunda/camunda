/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbUsageMetricState implements MutableUsageMetricState {

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbString> rPIColumnFamily;
  private final DbCompositeKey<DbLong, DbLong> eventTimePiKey;
  private final DbLong eventTimeKey;
  private final DbLong piKey;
  private final DbString tenantIdVal;

  public DbUsageMetricState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    eventTimeKey = new DbLong();
    piKey = new DbLong();
    tenantIdVal = new DbString();
    eventTimePiKey = new DbCompositeKey<>(eventTimeKey, piKey);

    rPIColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RPI_USAGE_METRICS, transactionContext, eventTimePiKey, tenantIdVal);
  }

  @Override
  public Map<String, List<Long>> getTenantIdPIsMapByEventTime(final long eventTime) {
    final var tenantIdPIsMap = new HashMap<String, List<Long>>();
    eventTimeKey.wrapLong(eventTime);
    rPIColumnFamily.whileEqualPrefix(
        eventTimeKey,
        (eventTimePiKey, tenantIdVal) -> {
          tenantIdPIsMap
              .computeIfAbsent(tenantIdVal.toString(), tenantId -> new ArrayList<>())
              .add(eventTimePiKey.second().getValue());
        });
    return tenantIdPIsMap;
  }

  @Override
  public void createRPIMetric(
      final long eventTime, final long processInstanceKey, final String tenantId) {
    eventTimeKey.wrapLong(eventTime);
    piKey.wrapLong(processInstanceKey);
    tenantIdVal.wrapString(tenantId);
    rPIColumnFamily.insert(eventTimePiKey, tenantIdVal);
  }

  @Override
  public void deleteByEventTime(final long eventTime) {
    eventTimeKey.wrapLong(eventTime);
    rPIColumnFamily.whileEqualPrefix(
        eventTimeKey,
        (eventTimePiKey, tenantIdVal) -> {
          rPIColumnFamily.deleteExisting(eventTimePiKey);
        });
  }
}
