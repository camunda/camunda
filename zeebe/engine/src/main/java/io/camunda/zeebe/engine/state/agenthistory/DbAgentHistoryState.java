/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agenthistory;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;

public final class DbAgentHistoryState implements MutableAgentHistoryState {

  // Primary store: historyItemKey -> AgentHistoryRecord
  private final DbLong historyItemKey = new DbLong();
  private final DbAgentHistory dbAgentHistory = new DbAgentHistory();
  private final ColumnFamily<DbLong, DbAgentHistory> agentHistoryColumnFamily;

  // Secondary index: (jobKey, jobLease, historyItemKey) -> nil
  // Supports prefix search by jobKey alone, or by (jobKey, jobLease)
  private final DbLong jobKey = new DbLong();
  private final DbString jobLease = new DbString();
  private final DbCompositeKey<DbLong, DbString> jobKeyAndLease =
      new DbCompositeKey<>(jobKey, jobLease);
  private final DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbLong>
      jobKeyLeaseAndHistoryItemKey = new DbCompositeKey<>(jobKeyAndLease, historyItemKey);
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbLong>, DbNil>
      byJobKeyColumnFamily;

  public DbAgentHistoryState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    agentHistoryColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_HISTORY, transactionContext, historyItemKey, dbAgentHistory);
    byJobKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_HISTORY_BY_JOB_KEY,
            transactionContext,
            jobKeyLeaseAndHistoryItemKey,
            DbNil.INSTANCE);
  }

  @Override
  public AgentHistoryRecord get(final long key) {
    historyItemKey.wrapLong(key);
    final var stored = agentHistoryColumnFamily.get(historyItemKey, DbAgentHistory::new);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public void visitByJobKey(final long jobKeyValue, final AgentHistoryVisitor visitor) {
    jobKey.wrapLong(jobKeyValue);
    byJobKeyColumnFamily.whileEqualPrefix(
        jobKey,
        (compositeKey, nil) -> {
          final var item = get(compositeKey.second().getValue());
          if (item != null) {
            visitor.visit(item);
          }
        });
  }

  @Override
  public void visitByJobLease(
      final long jobKeyValue, final String leaseValue, final AgentHistoryVisitor visitor) {
    jobKey.wrapLong(jobKeyValue);
    jobLease.wrapString(leaseValue);
    byJobKeyColumnFamily.whileEqualPrefix(
        jobKeyAndLease,
        (compositeKey, nil) -> {
          final var item = get(compositeKey.second().getValue());
          if (item != null) {
            visitor.visit(item);
          }
        });
  }

  @Override
  public void insert(final long key, final AgentHistoryRecord record) {
    historyItemKey.wrapLong(key);
    jobKey.wrapLong(record.getJobKey());
    jobLease.wrapString(record.getJobLease());
    dbAgentHistory.setRecord(record);
    agentHistoryColumnFamily.insert(historyItemKey, dbAgentHistory);
    byJobKeyColumnFamily.insert(jobKeyLeaseAndHistoryItemKey, DbNil.INSTANCE);
  }

  @Override
  public void delete(final long key) {
    final var existing = get(key);
    if (existing == null) {
      return;
    }
    delete(key, existing);
  }

  @Override
  public void delete(final long key, final AgentHistoryRecord record) {
    historyItemKey.wrapLong(key);
    jobKey.wrapLong(record.getJobKey());
    jobLease.wrapString(record.getJobLease());
    agentHistoryColumnFamily.deleteIfExists(historyItemKey);
    byJobKeyColumnFamily.deleteIfExists(jobKeyLeaseAndHistoryItemKey);
  }
}
