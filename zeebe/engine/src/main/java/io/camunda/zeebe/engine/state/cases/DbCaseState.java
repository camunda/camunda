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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DbCaseState implements MutableCaseState {

  private final DbLong caseKey = new DbLong();
  private final PersistedCase persistedCase = new PersistedCase();
  private final ColumnFamily<DbLong, PersistedCase> caseColumnFamily;

  private final DbString stageElementId = new DbString();
  private final DbCompositeKey<DbLong, DbString> caseKeyAndStageElementId =
      new DbCompositeKey<>(caseKey, stageElementId);
  private final PersistedCaseStage persistedStage = new PersistedCaseStage();
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, PersistedCaseStage>
      stageColumnFamily;

  public DbCaseState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    caseColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CASE, transactionContext, caseKey, persistedCase);
    stageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CASE_STAGE,
            transactionContext,
            caseKeyAndStageElementId,
            persistedStage);
  }

  @Override
  public boolean exists(final long caseKey) {
    this.caseKey.wrapLong(caseKey);
    return caseColumnFamily.exists(this.caseKey);
  }

  @Override
  public Optional<PersistedCase> getCase(final long caseKey) {
    this.caseKey.wrapLong(caseKey);
    return Optional.ofNullable(caseColumnFamily.get(this.caseKey, PersistedCase::new));
  }

  @Override
  public void forEachStage(
      final long caseKey, final BiConsumer<String, PersistedCaseStage> consumer) {
    this.caseKey.wrapLong(caseKey);
    stageColumnFamily.whileEqualPrefix(
        this.caseKey,
        (key, stage) -> {
          consumer.accept(key.second().toString(), stage);
        });
  }

  @Override
  public void create(final long caseKey, final CaseRecord record) {
    this.caseKey.wrapLong(caseKey);
    persistedCase
        .setTenantId(record.getTenantId())
        .setBpmnProcessId(record.getBpmnProcessId())
        .setBusinessId(record.getBusinessId())
        .setTitle(record.getTitle());
    caseColumnFamily.insert(this.caseKey, persistedCase);

    for (final var stage : record.getStages()) {
      stageElementId.wrapString(stage.getElementId());
      persistedStage.setStatus(stage.getStatus());
      stageColumnFamily.insert(caseKeyAndStageElementId, persistedStage);
    }
  }

  @Override
  public void updateBusinessId(final long caseKey, final String businessId) {
    this.caseKey.wrapLong(caseKey);
    final var existing = caseColumnFamily.get(this.caseKey, PersistedCase::new);
    if (existing == null) {
      throw new IllegalStateException(
          "Expected to update the business ID of case '%d', but no such case exists."
              .formatted(caseKey));
    }
    existing.setBusinessId(businessId);
    caseColumnFamily.update(this.caseKey, existing);
  }
}
