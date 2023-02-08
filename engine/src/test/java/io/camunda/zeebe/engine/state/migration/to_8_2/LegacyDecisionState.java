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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;

public class LegacyDecisionState {

  private final DbLong dbDecisionKey;
  private final PersistedDecision dbPersistedDecision;
  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKeyColumnFamily;

  public LegacyDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    dbDecisionKey = new DbLong();
    dbPersistedDecision = new PersistedDecision();
    decisionsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS, transactionContext, dbDecisionKey, dbPersistedDecision);
  }

  public void putDecision(final long key, final DecisionRecord decision) {
    dbDecisionKey.wrapLong(key);
    dbPersistedDecision.wrap(decision);
    decisionsByKeyColumnFamily.upsert(dbDecisionKey, dbPersistedDecision);
  }
}
