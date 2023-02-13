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

public class DecisionRequirementsMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final ZeebeState zeebeState) {
    return zeebeState.isEmpty(
            ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION)
        && !zeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_REQUIREMENTS);
  }

  @Override
  public void runMigration(final MutableZeebeState zeebeState) {
    zeebeState.getMigrationState().migrateDrgPopulateDrgVersionByDrgIdAndKey();
  }
}
