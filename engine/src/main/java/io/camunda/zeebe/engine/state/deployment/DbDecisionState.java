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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class DbDecisionState implements MutableDecisionState {

  private final DbLong dbDecisionKey;
  private final DbForeignKey<DbLong> fkDecision;
  private final PersistedDecision dbPersistedDecision;
  private final DbString dbDecisionId;

  private final DbLong dbDecisionRequirementsKey;
  private final DbForeignKey<DbLong> fkDecisionRequirements;
  private final PersistedDecisionRequirements dbPersistedDecisionRequirements;
  private final DbString dbDecisionRequirementsId;

  private final DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>
      dbDecisionRequirementsKeyAndDecisionKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
      decisionKeyByDecisionRequirementsKey;

  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKey;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> latestDecisionKeysByDecisionId;

  private final DbInt dbDecisionVersion;
  private final DbCompositeKey<DbString, DbForeignKey<DbLong>> decisionKeyByDecisionId;
  private final ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbLong>>, DbInt>
      decisionVersionByDecisionIdAndDecisionKey;

  private final ColumnFamily<DbLong, PersistedDecisionRequirements> decisionRequirementsByKey;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> latestDecisionRequirementsKeysById;

  public DbDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    dbDecisionKey = new DbLong();
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DMN_DECISIONS);

    dbPersistedDecision = new PersistedDecision();
    decisionsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS, transactionContext, dbDecisionKey, dbPersistedDecision);

    dbDecisionId = new DbString();
    latestDecisionKeysByDecisionId =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_BY_ID,
            transactionContext,
            dbDecisionId,
            fkDecision);

    dbDecisionRequirementsKey = new DbLong();
    fkDecisionRequirements =
        new DbForeignKey<>(dbDecisionRequirementsKey, ZbColumnFamilies.DMN_DECISION_REQUIREMENTS);
    dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
    decisionRequirementsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_REQUIREMENTS,
            transactionContext,
            dbDecisionRequirementsKey,
            dbPersistedDecisionRequirements);

    dbDecisionRequirementsId = new DbString();
    latestDecisionRequirementsKeysById =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
            transactionContext,
            dbDecisionRequirementsId,
            fkDecisionRequirements);

    dbDecisionRequirementsKeyAndDecisionKey =
        new DbCompositeKey<>(fkDecisionRequirements, fkDecision);
    decisionKeyByDecisionRequirementsKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
            transactionContext,
            dbDecisionRequirementsKeyAndDecisionKey,
            DbNil.INSTANCE);

    decisionKeyByDecisionId = new DbCompositeKey<>(dbDecisionId, fkDecision);
    dbDecisionVersion = new DbInt();
    decisionVersionByDecisionIdAndDecisionKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_VERSION_BY_DECISION_ID_AND_KEY,
            transactionContext,
            decisionKeyByDecisionId,
            dbDecisionVersion);
  }

  @Override
  public Optional<PersistedDecision> findLatestDecisionById(final DirectBuffer decisionId) {
    dbDecisionId.wrapBuffer(decisionId);

    return Optional.ofNullable(latestDecisionKeysByDecisionId.get(dbDecisionId))
        .flatMap(decisionKey -> findDecisionByKey(decisionKey.inner().getValue()));
  }

  @Override
  public Optional<PersistedDecision> findDecisionByKey(final long decisionKey) {
    dbDecisionKey.wrapLong(decisionKey);
    return Optional.ofNullable(decisionsByKey.get(dbDecisionKey)).map(PersistedDecision::copy);
  }

  @Override
  public Optional<PersistedDecisionRequirements> findLatestDecisionRequirementsById(
      final DirectBuffer decisionRequirementsId) {
    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);

    return Optional.ofNullable(latestDecisionRequirementsKeysById.get(dbDecisionRequirementsId))
        .map((requirementsKey) -> requirementsKey.inner().getValue())
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
          findDecisionByKey(decisionKey.inner().getValue()).ifPresent(decisions::add);
        }));

    return decisions;
  }

  @Override
  public void storeDecisionRecord(final DecisionRecord record) {
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbPersistedDecision.wrap(record);
    decisionsByKey.upsert(dbDecisionKey, dbPersistedDecision);

    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    decisionKeyByDecisionRequirementsKey.upsert(
        dbDecisionRequirementsKeyAndDecisionKey, DbNil.INSTANCE);

    updateLatestDecisionVersion(record);
  }

  @Override
  public void storeDecisionRequirements(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbPersistedDecisionRequirements.wrap(record);
    decisionRequirementsByKey.upsert(dbDecisionRequirementsKey, dbPersistedDecisionRequirements);

    updateLatestDecisionRequirementsVersion(record);
  }

  private void updateLatestDecisionVersion(final DecisionRecord record) {
    findLatestDecisionById(record.getDecisionIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getVersion() > previousVersion.getVersion()) {
                updateDecisionAsLatestVersion(record);
              }
            },
            () -> insertDecisionAsLatestVersion(record));
  }

  private void updateDecisionAsLatestVersion(final DecisionRecord record) {
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    latestDecisionKeysByDecisionId.update(dbDecisionId, fkDecision);
  }

  private void insertDecisionAsLatestVersion(final DecisionRecord record) {
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    latestDecisionKeysByDecisionId.upsert(dbDecisionId, fkDecision);
  }

  private void updateLatestDecisionRequirementsVersion(final DecisionRequirementsRecord record) {
    findLatestDecisionRequirementsById(record.getDecisionRequirementsIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getDecisionRequirementsVersion()
                  > previousVersion.getDecisionRequirementsVersion()) {
                updateDecisionRequirementsAsLatestVersion(record);
              }
            },
            () -> insertDecisionRequirementsAsLatestVersion(record));
  }

  private void updateDecisionRequirementsAsLatestVersion(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    latestDecisionRequirementsKeysById.update(dbDecisionRequirementsId, fkDecisionRequirements);
  }

  private void insertDecisionRequirementsAsLatestVersion(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    latestDecisionRequirementsKeysById.upsert(dbDecisionRequirementsId, fkDecisionRequirements);
  }
}
