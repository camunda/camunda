/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;

/** Reads out the temporary variable column and creates an EventTrigger for reach of them. */
public class TemporaryVariableMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return TemporaryVariableMigration.class.getSimpleName();
  }

  @Override
  public boolean needsToRun(final ZeebeState zeebeState) {
    return !zeebeState.isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE);
  }

  @Override
  public void runMigration(final MutableZeebeState zeebeState) {
    zeebeState
        .getMigrationState()
        .migrateTemporaryVariables(
            zeebeState.getEventScopeInstanceState(), zeebeState.getElementInstanceState());
  }
}
