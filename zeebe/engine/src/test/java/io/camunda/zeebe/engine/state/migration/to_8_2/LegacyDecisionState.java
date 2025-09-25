/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.common.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.common.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;

public class LegacyDecisionState {

  private final DbLong dbDecisionKey;
  private final PersistedDecision dbPersistedDecision;
  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKeyColumnFamily;

  private final DbLong dbDecisionRequirementsKey;
  private final PersistedDecisionRequirements dbPersistedDecisionRequirements;
  private final ColumnFamily<DbLong, PersistedDecisionRequirements> decisionRequirementsByKey;

  public LegacyDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    dbDecisionKey = new DbLong();
    dbPersistedDecision = new PersistedDecision();
    decisionsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISIONS,
            transactionContext,
            dbDecisionKey,
            dbPersistedDecision);

    dbDecisionRequirementsKey = new DbLong();
    dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
    decisionRequirementsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS,
            transactionContext,
            dbDecisionRequirementsKey,
            dbPersistedDecisionRequirements);
  }

  public void putDecision(final long key, final DecisionRecord decision) {
    dbDecisionKey.wrapLong(key);
    dbPersistedDecision.wrap(decision);
    decisionsByKeyColumnFamily.upsert(dbDecisionKey, dbPersistedDecision);
  }

  public void putDecisionRequirements(final long key, final DecisionRequirementsRecord drg) {
    dbDecisionRequirementsKey.wrapLong(key);
    dbPersistedDecisionRequirements.wrap(drg);
    decisionRequirementsByKey.upsert(dbDecisionRequirementsKey, dbPersistedDecisionRequirements);
  }
}
