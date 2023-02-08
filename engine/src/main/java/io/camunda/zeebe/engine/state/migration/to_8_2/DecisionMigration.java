/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * This migration will read the decision in the decisionByKey ColumnFamily. It will use this to
 * populate the decisionVersionByDecisionIdAndDecisionKey ColumnFamily, which is used when deleting
 * a decision from the state.
 */
public class DecisionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final ZeebeState zeebeState) {
    return zeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION)
        && !zeebeState.isEmpty(ZbColumnFamilies.DMN_DECISIONS);
  }

  @Override
  public void runMigration(final MutableZeebeState zeebeState) {
    zeebeState
        .getMigrationState()
        .migrateDecisionsPopulateDecisionVersionByDecisionIdAndDecisionKey();
  }
}
