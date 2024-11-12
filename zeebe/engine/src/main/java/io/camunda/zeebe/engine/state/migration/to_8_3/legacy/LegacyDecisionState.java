/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3.legacy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;

public final class LegacyDecisionState {

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

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
  private final DbCompositeKey<DbString, DbInt> decisionIdAndVersion;
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      decisionKeyByDecisionIdAndVersion;

  private final ColumnFamily<DbLong, PersistedDecisionRequirements> decisionRequirementsByKey;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> latestDecisionRequirementsKeysById;

  private final DbInt dbDecisionRequirementsVersion;
  private final DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      decisionRequirementsKeyByIdAndVersion;

  private final LoadingCache<Long, DeployedDrg> drgCache;

  public LegacyDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {
    dbDecisionKey = new DbLong();
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DEPRECATED_DMN_DECISIONS);

    dbPersistedDecision = new PersistedDecision();
    decisionsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISIONS,
            transactionContext,
            dbDecisionKey,
            dbPersistedDecision);

    dbDecisionId = new DbString();
    latestDecisionKeysByDecisionId =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_LATEST_DECISION_BY_ID,
            transactionContext,
            dbDecisionId,
            fkDecision);

    dbDecisionRequirementsKey = new DbLong();
    fkDecisionRequirements =
        new DbForeignKey<>(
            dbDecisionRequirementsKey, ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS);
    dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
    decisionRequirementsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS,
            transactionContext,
            dbDecisionRequirementsKey,
            dbPersistedDecisionRequirements);

    dbDecisionRequirementsId = new DbString();
    latestDecisionRequirementsKeysById =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
            transactionContext,
            dbDecisionRequirementsId,
            fkDecisionRequirements);

    dbDecisionRequirementsKeyAndDecisionKey =
        new DbCompositeKey<>(fkDecisionRequirements, fkDecision);
    decisionKeyByDecisionRequirementsKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
            transactionContext,
            dbDecisionRequirementsKeyAndDecisionKey,
            DbNil.INSTANCE);

    dbDecisionVersion = new DbInt();
    decisionIdAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
    decisionKeyByDecisionIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
            transactionContext,
            decisionIdAndVersion,
            fkDecision);

    dbDecisionRequirementsVersion = new DbInt();
    decisionRequirementsIdAndVersion =
        new DbCompositeKey<>(dbDecisionRequirementsId, dbDecisionRequirementsVersion);
    decisionRequirementsKeyByIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies
                .DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
            transactionContext,
            decisionRequirementsIdAndVersion,
            fkDecisionRequirements);

    drgCache =
        CacheBuilder.newBuilder()
            .maximumSize(config.getDrgCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public DeployedDrg load(final Long key) throws DrgNotFoundException {
                    return findAndParseDecisionRequirementsByKeyFromDb(key);
                  }
                });
  }

  public Optional<PersistedDecision> findLatestDecisionById(final DirectBuffer decisionId) {
    dbDecisionId.wrapBuffer(decisionId);

    return Optional.ofNullable(latestDecisionKeysByDecisionId.get(dbDecisionId))
        .flatMap(decisionKey -> findDecisionByKey(decisionKey.inner().getValue()));
  }

  public Optional<PersistedDecision> findDecisionByKey(final long decisionKey) {
    dbDecisionKey.wrapLong(decisionKey);
    return Optional.ofNullable(decisionsByKey.get(dbDecisionKey)).map(PersistedDecision::copy);
  }

  public Optional<DeployedDrg> findLatestDecisionRequirementsById(
      final DirectBuffer decisionRequirementsId) {
    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);

    return Optional.ofNullable(latestDecisionRequirementsKeysById.get(dbDecisionRequirementsId))
        .map((requirementsKey) -> requirementsKey.inner().getValue())
        .flatMap(this::findDecisionRequirementsByKey);
  }

  public Optional<DeployedDrg> findDecisionRequirementsByKey(final long decisionRequirementsKey) {
    return findDeployedDrg(decisionRequirementsKey);
  }

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

  public void clearCache() {
    drgCache.invalidateAll();
  }

  private DeployedDrg findAndParseDecisionRequirementsByKeyFromDb(
      final long decisionRequirementsKey) throws DrgNotFoundException {
    dbDecisionRequirementsKey.wrapLong(decisionRequirementsKey);

    final PersistedDecisionRequirements persistedDrg =
        decisionRequirementsByKey.get(dbDecisionRequirementsKey);
    if (persistedDrg == null) {
      throw new DrgNotFoundException();
    }

    final PersistedDecisionRequirements copiedDrg = persistedDrg.copy();

    final var resourceBytes = BufferUtil.bufferAsArray(copiedDrg.getResource());
    final ParsedDecisionRequirementsGraph parsedDrg =
        decisionEngine.parse(new ByteArrayInputStream(resourceBytes));

    return new DeployedDrg(parsedDrg, copiedDrg);
  }

  private Optional<DeployedDrg> findDeployedDrg(final long decisionRequirementsKey) {
    try {
      // The cache automatically fetches it from the state if the key does not exist.
      return Optional.of(drgCache.get(decisionRequirementsKey));
    } catch (final ExecutionException e) {
      // We reach this when we couldn't load the DRG from the state.
      return Optional.empty();
    }
  }

  /**
   * Query decisions to find the key of the decision with the version that comes before the given
   * version.
   *
   * @param decisionId the id of the decision
   * @param currentVersion the current version
   * @return the decision key of the version that's previous to the given version
   */
  private Optional<Long> findPreviousVersionDecisionKey(
      final DirectBuffer decisionId, final int currentVersion) {
    final Map<Integer, Long> decisionKeysByVersion = new HashMap<>();

    dbDecisionId.wrapBuffer(decisionId);
    decisionKeyByDecisionIdAndVersion.whileEqualPrefix(
        dbDecisionId,
        ((key, decisionKey) -> {
          if (key.second().getValue() < currentVersion) {
            decisionKeysByVersion.put(key.second().getValue(), decisionKey.inner().getValue());
          }
        }));

    if (decisionKeysByVersion.isEmpty()) {
      return Optional.empty();
    } else {
      final Integer previousVersion = Collections.max(decisionKeysByVersion.keySet());
      return Optional.of(decisionKeysByVersion.get(previousVersion));
    }
  }

  private Optional<Long> findPreviousVersionDecisionRequirementsKey(
      final DirectBuffer decisionRequirementsId, final int currentVersion) {
    final Map<Integer, Long> decisionRequirementsKeysByVersion = new HashMap<>();

    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);
    decisionRequirementsKeyByIdAndVersion.whileEqualPrefix(
        dbDecisionRequirementsId,
        ((key, drgKey) -> {
          if (key.second().getValue() < currentVersion) {
            decisionRequirementsKeysByVersion.put(
                key.second().getValue(), drgKey.inner().getValue());
          }
        }));

    if (decisionRequirementsKeysByVersion.isEmpty()) {
      return Optional.empty();
    } else {
      final Integer previousVersion = Collections.max(decisionRequirementsKeysByVersion.keySet());
      return Optional.of(decisionRequirementsKeysByVersion.get(previousVersion));
    }
  }

  public void storeDecisionRecord(final DecisionRecord record) {
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbPersistedDecision.wrap(record);
    decisionsByKey.upsert(dbDecisionKey, dbPersistedDecision);

    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    decisionKeyByDecisionRequirementsKey.upsert(
        dbDecisionRequirementsKeyAndDecisionKey, DbNil.INSTANCE);

    dbDecisionId.wrapString(record.getDecisionId());
    dbDecisionVersion.wrapInt(record.getVersion());
    decisionKeyByDecisionIdAndVersion.upsert(decisionIdAndVersion, fkDecision);

    updateLatestDecisionVersion(record);
  }

  public void storeDecisionRequirements(final DecisionRequirementsRecord record) {
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbPersistedDecisionRequirements.wrap(record);
    decisionRequirementsByKey.upsert(dbDecisionRequirementsKey, dbPersistedDecisionRequirements);

    dbDecisionRequirementsId.wrapString(record.getDecisionRequirementsId());
    dbDecisionRequirementsVersion.wrapInt(record.getDecisionRequirementsVersion());
    decisionRequirementsKeyByIdAndVersion.upsert(
        decisionRequirementsIdAndVersion, fkDecisionRequirements);

    updateLatestDecisionRequirementsVersion(record);
  }

  public void deleteDecision(final DecisionRecord record) {
    findLatestDecisionById(record.getDecisionIdBuffer())
        .map(PersistedDecision::getVersion)
        .ifPresent(
            latestVersion -> {
              if (latestVersion == record.getVersion()) {
                dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
                findPreviousVersionDecisionKey(record.getDecisionIdBuffer(), record.getVersion())
                    .ifPresentOrElse(
                        previousDecisionKey -> {
                          // Update the latest decision version
                          dbDecisionKey.wrapLong(previousDecisionKey);
                          latestDecisionKeysByDecisionId.update(dbDecisionId, fkDecision);
                        },
                        () -> {
                          // Clear the latest decision version
                          latestDecisionKeysByDecisionId.deleteExisting(dbDecisionId);
                        });
              }
            });

    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionVersion.wrapInt(record.getVersion());

    decisionKeyByDecisionRequirementsKey.deleteExisting(dbDecisionRequirementsKeyAndDecisionKey);
    decisionsByKey.deleteExisting(dbDecisionKey);
    decisionKeyByDecisionIdAndVersion.deleteExisting(decisionIdAndVersion);
  }

  public void deleteDecisionRequirements(final DecisionRequirementsRecord record) {
    findLatestDecisionRequirementsById(record.getDecisionRequirementsIdBuffer())
        .map(DeployedDrg::getDecisionRequirementsVersion)
        .ifPresent(
            latestVersion -> {
              if (latestVersion == record.getDecisionRequirementsVersion()) {
                dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
                findPreviousVersionDecisionRequirementsKey(
                        record.getDecisionRequirementsIdBuffer(),
                        record.getDecisionRequirementsVersion())
                    .ifPresentOrElse(
                        previousDrgKey -> {
                          // Update the latest decision version
                          dbDecisionRequirementsKey.wrapLong(previousDrgKey);
                          latestDecisionRequirementsKeysById.update(
                              dbDecisionRequirementsId, fkDecisionRequirements);
                        },
                        () -> {
                          // Clear the latest decision version
                          latestDecisionRequirementsKeysById.deleteExisting(
                              dbDecisionRequirementsId);
                        });
              }
            });

    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsVersion.wrapInt(record.getDecisionRequirementsVersion());

    decisionRequirementsByKey.deleteExisting(dbDecisionRequirementsKey);
    decisionRequirementsKeyByIdAndVersion.deleteExisting(decisionRequirementsIdAndVersion);
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

  public ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
      getDecisionKeyByDecisionRequirementsKey() {
    return decisionKeyByDecisionRequirementsKey;
  }

  public ColumnFamily<DbLong, PersistedDecision> getDecisionsByKey() {
    return decisionsByKey;
  }

  public ColumnFamily<DbString, DbForeignKey<DbLong>> getLatestDecisionKeysByDecisionId() {
    return latestDecisionKeysByDecisionId;
  }

  public ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      getDecisionKeyByDecisionIdAndVersion() {
    return decisionKeyByDecisionIdAndVersion;
  }

  public ColumnFamily<DbLong, PersistedDecisionRequirements> getDecisionRequirementsByKey() {
    return decisionRequirementsByKey;
  }

  public ColumnFamily<DbString, DbForeignKey<DbLong>> getLatestDecisionRequirementsKeysById() {
    return latestDecisionRequirementsKeysById;
  }

  public ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      getDecisionRequirementsKeyByIdAndVersion() {
    return decisionRequirementsKeyByIdAndVersion;
  }

  /**
   * This exception is thrown when the drgCache can't find a DRG in the state for a given key. This
   * must be a checked exception, because of the way the {@link LoadingCache} works.
   */
  private static final class DrgNotFoundException extends Exception {}
}
