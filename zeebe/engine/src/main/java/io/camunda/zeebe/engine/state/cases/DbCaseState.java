/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.cases;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DbCaseState implements MutableCaseState {

  private final DbLong caseKey = new DbLong();
  private final CaseRecordDbValue caseValue = new CaseRecordDbValue();
  private final ColumnFamily<DbLong, CaseRecordDbValue> caseColumnFamily;

  private final DbLong rootProcessInstanceKey = new DbLong();
  private final DbForeignKey<DbLong> fkCaseKey = new DbForeignKey<>(caseKey, ZbColumnFamilies.CASE);
  private final ColumnFamily<DbLong, DbForeignKey<DbLong>> caseKeyByRootProcessInstanceKey;

  public DbCaseState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    caseColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.CASE, transactionContext, caseKey, caseValue);
    caseKeyByRootProcessInstanceKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CASE_KEY_BY_ROOT_PROCESS_INSTANCE_KEY,
            transactionContext,
            rootProcessInstanceKey,
            fkCaseKey);
  }

  @Override
  public void createCase(final CaseRecord caseRecord) {
    caseKey.wrapLong(caseRecord.getCaseKey());
    caseValue.setRecord(caseRecord);
    caseColumnFamily.insert(caseKey, caseValue);

    rootProcessInstanceKey.wrapLong(caseRecord.getProcessInstanceKey());
    caseKeyByRootProcessInstanceKey.insert(rootProcessInstanceKey, fkCaseKey);
  }

  @Override
  public void updateCase(final CaseRecord caseRecord) {
    caseKey.wrapLong(caseRecord.getCaseKey());
    caseValue.setRecord(caseRecord);
    caseColumnFamily.update(caseKey, caseValue);
  }

  @Override
  public @Nullable CaseRecord getCase(final long caseKey) {
    this.caseKey.wrapLong(caseKey);
    final var stored = caseColumnFamily.get(this.caseKey, CaseRecordDbValue::new);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public @Nullable CaseRecord getCaseByProcessInstanceKey(final long processInstanceKey) {
    rootProcessInstanceKey.wrapLong(processInstanceKey);
    final var foreignKey = caseKeyByRootProcessInstanceKey.get(rootProcessInstanceKey);
    if (foreignKey == null) {
      return null;
    }

    return getCase(foreignKey.inner().getValue());
  }
}
