/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.migration.MemoryBoundedColumnIteration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyDecisionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbDecisionMigrationState {

  private final LegacyDecisionState from;
  private final DbDecisionState to;

  public DbDecisionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    final var config = new EngineConfiguration();
    from = new LegacyDecisionState(zeebeDb, transactionContext, config);
    to = new DbDecisionState(zeebeDb, transactionContext);
  }

  public void migrateDecisionStateForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    `DEPRECATED_DMN_DECISIONS` -> `DMN_DECISIONS`
    - Prefix tenant to key
    - Set tenant in value (`PersistedDecision`)
    */
    iterator.drain(
        from.getDecisionsByKey(),
        (key, value) -> {
          value.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
          to.dbDecisionKey.wrapLong(key.getValue());
          to.decisionsByKey.insert(to.tenantAwareDecisionKey, value);
        });

    /*
    `DEPRECATED_DMN_DECISION_REQUIREMENTS` -> `DMN_DECISION_REQUIREMENTS`
    - Prefix tenant to key
    - Set tenant in value (`PersistedDecisionRequirements`)
    */
    iterator.drain(
        from.getDecisionRequirementsByKey(),
        (key, value) -> {
          value.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
          to.dbDecisionRequirementsKey.wrapLong(key.getValue());
          to.decisionRequirementsByKey.insert(to.tenantAwareDecisionRequirementsKey, value);
        });

    /*
    `DEPRECATED_DMN_LATEST_DECISION_BY_ID` -> `DMN_LATEST_DECISION_BY_ID`
    - Prefix tenant to key
    - Prefix tenant to key in value (Foreign key to the Decision key)
     */
    iterator.drain(
        from.getLatestDecisionKeysByDecisionId(),
        (key, value) -> {
          to.dbDecisionId.wrapBuffer(key.getBuffer());
          to.dbDecisionKey.wrapLong(value.inner().getValue());
          to.latestDecisionKeysByDecisionId.insert(to.tenantAwareDecisionId, to.fkDecision);
        });

    /*
    `DEPRECATED_DMN_LATEST_DECISION_REQUIREMENTS_BY_ID` -> `DMN_LATEST_DECISION_REQUIREMENTS_BY_ID`
    - Prefix tenant to key
    - Prefix tenant to key in value (Foreign key to Decision Requirements key)
    */
    iterator.drain(
        from.getLatestDecisionRequirementsKeysById(),
        (key, value) -> {
          to.dbDecisionRequirementsId.wrapBuffer(key.getBuffer());
          to.dbDecisionRequirementsKey.wrapLong(value.inner().getValue());
          to.latestDecisionRequirementsKeysById.insert(
              to.tenantAwareDecisionRequirementsId, to.fkDecisionRequirements);
        });

    /*
    `DEPRECATED_DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY` -> `DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY`
    - The key for this CF is a composite key. The tenant must be prefixed to both pats of the composite key.
     */
    iterator.drain(
        from.getDecisionKeyByDecisionRequirementsKey(),
        (key, value) -> {
          to.dbDecisionRequirementsKey.wrapLong(key.first().inner().getValue());
          to.dbDecisionKey.wrapLong(key.second().inner().getValue());
          to.decisionKeyByDecisionRequirementsKey.insert(
              to.dbDecisionRequirementsKeyAndDecisionKey, DbNil.INSTANCE);
        });

    /*
    `DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION` -> `DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION`
    - Prefix tenant to key
    - Prefix tenant to key in value (Foreign key to Decision key)
     */
    iterator.drain(
        from.getDecisionKeyByDecisionIdAndVersion(),
        (key, value) -> {
          to.dbDecisionId.wrapBuffer(key.first().getBuffer());
          to.dbDecisionVersion.wrapInt(key.second().getValue());
          to.dbDecisionKey.wrapLong(value.inner().getValue());
          to.decisionKeyByDecisionIdAndVersion.insert(
              to.tenantAwareDecisionIdAndVersion, to.fkDecision);
        });

    /*
    `DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION` -> `DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION`
    - Prefix tenant to key
    - Prefix tenant to key in value (Foreign key to Decision Requirements key)
     */
    iterator.drain(
        from.getDecisionRequirementsKeyByIdAndVersion(),
        (key, value) -> {
          to.dbDecisionRequirementsId.wrapBuffer(key.first().getBuffer());
          to.dbDecisionRequirementsVersion.wrapInt(key.second().getValue());
          to.dbDecisionRequirementsKey.wrapLong(value.inner().getValue());
          to.decisionRequirementsKeyByIdAndVersion.insert(
              to.tenantAwareDecisionRequirementsIdAndVersion, to.fkDecisionRequirements);
        });
  }

  private static final class DbDecisionState {
    private final DbString tenantIdKey;
    private final DbLong dbDecisionKey;
    private final DbTenantAwareKey<DbLong> tenantAwareDecisionKey;
    private final DbForeignKey<DbTenantAwareKey<DbLong>> fkDecision;
    private final PersistedDecision dbPersistedDecision;
    private final DbString dbDecisionId;
    private final DbTenantAwareKey<DbString> tenantAwareDecisionId;

    private final DbLong dbDecisionRequirementsKey;
    private final DbTenantAwareKey<DbLong> tenantAwareDecisionRequirementsKey;
    private final DbForeignKey<DbTenantAwareKey<DbLong>> fkDecisionRequirements;
    private final PersistedDecisionRequirements dbPersistedDecisionRequirements;
    private final DbString dbDecisionRequirementsId;
    private final DbTenantAwareKey<DbString> tenantAwareDecisionRequirementsId;
    private final DbCompositeKey<
            DbForeignKey<DbTenantAwareKey<DbLong>>, DbForeignKey<DbTenantAwareKey<DbLong>>>
        dbDecisionRequirementsKeyAndDecisionKey;
    private final ColumnFamily<
            DbCompositeKey<
                DbForeignKey<DbTenantAwareKey<DbLong>>, DbForeignKey<DbTenantAwareKey<DbLong>>>,
            DbNil>
        decisionKeyByDecisionRequirementsKey;

    private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedDecision> decisionsByKey;
    private final ColumnFamily<DbTenantAwareKey<DbString>, DbForeignKey<DbTenantAwareKey<DbLong>>>
        latestDecisionKeysByDecisionId;

    private final DbInt dbDecisionVersion;
    private final DbCompositeKey<DbString, DbInt> decisionIdAndVersion;
    private final DbTenantAwareKey<DbCompositeKey<DbString, DbInt>> tenantAwareDecisionIdAndVersion;

    private final ColumnFamily<
            DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>,
            DbForeignKey<DbTenantAwareKey<DbLong>>>
        decisionKeyByDecisionIdAndVersion;

    private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedDecisionRequirements>
        decisionRequirementsByKey;
    private final ColumnFamily<DbTenantAwareKey<DbString>, DbForeignKey<DbTenantAwareKey<DbLong>>>
        latestDecisionRequirementsKeysById;

    private final DbInt dbDecisionRequirementsVersion;
    private final DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
    private final DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>
        tenantAwareDecisionRequirementsIdAndVersion;

    private final ColumnFamily<
            DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>,
            DbForeignKey<DbTenantAwareKey<DbLong>>>
        decisionRequirementsKeyByIdAndVersion;

    public DbDecisionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      tenantIdKey = new DbString();
      dbDecisionKey = new DbLong();
      tenantAwareDecisionKey =
          new DbTenantAwareKey<>(tenantIdKey, dbDecisionKey, PlacementType.PREFIX);
      fkDecision = new DbForeignKey<>(tenantAwareDecisionKey, ZbColumnFamilies.DMN_DECISIONS);

      dbPersistedDecision = new PersistedDecision();
      decisionsByKey =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISIONS,
              transactionContext,
              tenantAwareDecisionKey,
              dbPersistedDecision);

      dbDecisionId = new DbString();
      tenantAwareDecisionId =
          new DbTenantAwareKey<>(tenantIdKey, dbDecisionId, PlacementType.PREFIX);
      latestDecisionKeysByDecisionId =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_LATEST_DECISION_BY_ID,
              transactionContext,
              tenantAwareDecisionId,
              fkDecision);

      dbDecisionRequirementsKey = new DbLong();
      tenantAwareDecisionRequirementsKey =
          new DbTenantAwareKey<>(tenantIdKey, dbDecisionRequirementsKey, PlacementType.PREFIX);
      fkDecisionRequirements =
          new DbForeignKey<>(
              tenantAwareDecisionRequirementsKey, ZbColumnFamilies.DMN_DECISION_REQUIREMENTS);
      dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
      decisionRequirementsByKey =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_REQUIREMENTS,
              transactionContext,
              tenantAwareDecisionRequirementsKey,
              dbPersistedDecisionRequirements);

      dbDecisionRequirementsId = new DbString();
      tenantAwareDecisionRequirementsId =
          new DbTenantAwareKey<>(tenantIdKey, dbDecisionRequirementsId, PlacementType.PREFIX);
      latestDecisionRequirementsKeysById =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
              transactionContext,
              tenantAwareDecisionRequirementsId,
              fkDecisionRequirements);

      dbDecisionRequirementsKeyAndDecisionKey =
          new DbCompositeKey<>(fkDecisionRequirements, fkDecision);
      decisionKeyByDecisionRequirementsKey =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
              transactionContext,
              dbDecisionRequirementsKeyAndDecisionKey,
              DbNil.INSTANCE);

      dbDecisionVersion = new DbInt();
      decisionIdAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
      tenantAwareDecisionIdAndVersion =
          new DbTenantAwareKey<>(tenantIdKey, decisionIdAndVersion, PlacementType.PREFIX);
      decisionKeyByDecisionIdAndVersion =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
              transactionContext,
              tenantAwareDecisionIdAndVersion,
              fkDecision);

      dbDecisionRequirementsVersion = new DbInt();
      decisionRequirementsIdAndVersion =
          new DbCompositeKey<>(dbDecisionRequirementsId, dbDecisionRequirementsVersion);
      tenantAwareDecisionRequirementsIdAndVersion =
          new DbTenantAwareKey<>(
              tenantIdKey, decisionRequirementsIdAndVersion, PlacementType.PREFIX);
      decisionRequirementsKeyByIdAndVersion =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
              transactionContext,
              tenantAwareDecisionRequirementsIdAndVersion,
              fkDecisionRequirements);
    }
  }
}
