/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;

public class LegacyDecisionState {

  private final DbLong dbDecisionKey;
  private final PersistedDecision dbPersistedDecision;
  private final DbString dbDecisionId;
  private final DbForeignKey<DbLong> fkDecision;
  private final DbInt dbDecisionVersion;
  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKeyColumnFamily;
  private final DbCompositeKey<DbString, DbForeignKey<DbLong>> decisionKeyByDecisionId;
  private final ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbLong>>, DbInt>
      decisionVersionByDecisionIdAndDecisionKey;

  public LegacyDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    dbDecisionKey = new DbLong();
    dbPersistedDecision = new PersistedDecision();
    decisionsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS, transactionContext, dbDecisionKey, dbPersistedDecision);
    dbDecisionId = new DbString();
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DMN_DECISIONS);
    decisionKeyByDecisionId = new DbCompositeKey<>(dbDecisionId, fkDecision);
    dbDecisionVersion = new DbInt();
    decisionVersionByDecisionIdAndDecisionKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_VERSION_BY_DECISION_ID_AND_KEY,
            transactionContext,
            decisionKeyByDecisionId,
            dbDecisionVersion);
  }

  public void putDecision(final long key, final DecisionRecord decision) {
    dbDecisionKey.wrapLong(key);
    dbPersistedDecision.wrap(decision);
    decisionsByKeyColumnFamily.upsert(dbDecisionKey, dbPersistedDecision);
  }
}
