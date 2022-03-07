/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class DbDecisionState implements MutableDecisionState {

  private final DbLong dbDecisionKey = new DbLong();
  private final PersistedDecision dbPersistedDecision = new PersistedDecision();
  private final DbString dbDecisionId = new DbString();

  private final DbLong dbDecisionRequirementsKey = new DbLong();
  private final PersistedDecisionRequirements dbPersistedDecisionRequirements =
      new PersistedDecisionRequirements();
  private final DbString dbDecisionRequirementsId = new DbString();

  private final DbCompositeKey<DbLong, DbLong> dbDecisionRequirementsKeyAndDecisionKey =
      new DbCompositeKey<>(dbDecisionRequirementsKey, dbDecisionKey);

  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKey;
  private final ColumnFamily<DbString, DbLong> latestDecisionKeysByDecisionId;

  private final ColumnFamily<DbLong, PersistedDecisionRequirements> decisionRequirementsByKey;
  private final ColumnFamily<DbString, DbLong> latestDecisionRequirementsKeysById;

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      decisionKeyByDecisionRequirementsKey;

  public DbDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    decisionsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS, transactionContext, dbDecisionKey, dbPersistedDecision);

    latestDecisionKeysByDecisionId =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_BY_ID,
            transactionContext,
            dbDecisionId,
            dbDecisionKey);

    decisionRequirementsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_REQUIREMENTS,
            transactionContext,
            dbDecisionRequirementsKey,
            dbPersistedDecisionRequirements);

    latestDecisionRequirementsKeysById =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
            transactionContext,
            dbDecisionRequirementsId,
            dbDecisionRequirementsKey);

    decisionKeyByDecisionRequirementsKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
            transactionContext,
            dbDecisionRequirementsKeyAndDecisionKey,
            DbNil.INSTANCE);
  }

  @Override
  public Optional<PersistedDecision> findLatestDecisionById(final DirectBuffer decisionId) {
    dbDecisionId.wrapBuffer(decisionId);

    return Optional.ofNullable(latestDecisionKeysByDecisionId.get(dbDecisionId))
        .flatMap(decisionKey -> findDecisionByKey(decisionKey.getValue()));
  }

  @Override
  public Optional<PersistedDecisionRequirements> findLatestDecisionRequirementsById(
      final DirectBuffer decisionRequirementsId) {
    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);

    return Optional.ofNullable(latestDecisionRequirementsKeysById.get(dbDecisionRequirementsId))
        .map(DbLong::getValue)
        .flatMap(this::findDecisionRequirementsByKey);
  }

  @Override
  public Optional<PersistedDecisionRequirements> findDecisionRequirementsByKey(
      final long decisionRequirementsKey) {
    dbDecisionRequirementsKey.wrapLong(decisionRequirementsKey);

    return Optional.ofNullable(decisionRequirementsByKey.get(dbDecisionRequirementsKey))
        .map(PersistedDecisionRequirements::copy);
  }

  @Override
  public List<PersistedDecision> findDecisionsByDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    final List<PersistedDecision> decisions = new ArrayList<>();

    dbDecisionRequirementsKey.wrapLong(decisionRequirementsKey);
    decisionKeyByDecisionRequirementsKey.whileEqualPrefix(
        dbDecisionRequirementsKey,
        ((key, nil) -> {
          final var decisionKey = key.second();
          findDecisionByKey(decisionKey.getValue()).ifPresent(decisions::add);
        }));

    return decisions;
  }

  private Optional<PersistedDecision> findDecisionByKey(final long decisionKey) {
    dbDecisionKey.wrapLong(decisionKey);
    return Optional.ofNullable(decisionsByKey.get(dbDecisionKey)).map(PersistedDecision::copy);
  }

  @Override
  public void putDecision(final DecisionRecord record) {
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbPersistedDecision.wrap(record);
    decisionsByKey.put(dbDecisionKey, dbPersistedDecision);

    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    decisionKeyByDecisionRequirementsKey.put(
        dbDecisionRequirementsKeyAndDecisionKey, DbNil.INSTANCE);

    updateLatestDecisionVersion(record);
  }

  @Override
  public void putDecisionRequirements(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbPersistedDecisionRequirements.wrap(record);
    decisionRequirementsByKey.put(dbDecisionRequirementsKey, dbPersistedDecisionRequirements);

    updateLatestDecisionRequirementsVersion(record);
  }

  private void updateLatestDecisionVersion(final DecisionRecord record) {
    findLatestDecisionById(record.getDecisionIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getVersion() > previousVersion.getVersion()) {
                putDecisionAsLatestVersion(record);
              }
            },
            () -> putDecisionAsLatestVersion(record));
  }

  private void putDecisionAsLatestVersion(final DecisionRecord record) {
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    latestDecisionKeysByDecisionId.put(dbDecisionId, dbDecisionKey);
  }

  private void updateLatestDecisionRequirementsVersion(final DecisionRequirementsRecord record) {
    findLatestDecisionRequirementsById(record.getDecisionRequirementsIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getDecisionRequirementsVersion()
                  > previousVersion.getDecisionRequirementsVersion()) {
                putDecisionRequirementsAsLatestVersion(record);
              }
            },
            () -> putDecisionRequirementsAsLatestVersion(record));
  }

  private void putDecisionRequirementsAsLatestVersion(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    latestDecisionRequirementsKeysById.put(dbDecisionRequirementsId, dbDecisionRequirementsKey);
  }
}
