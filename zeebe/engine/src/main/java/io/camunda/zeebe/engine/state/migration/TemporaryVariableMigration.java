/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.protocol.ZbColumnFamilies;

/** Reads out the temporary variable column and creates an EventTrigger for reach of them. */
public class TemporaryVariableMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return TemporaryVariableMigration.class.getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return !context.processingState().isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context
        .processingState()
        .getMigrationState()
        .migrateTemporaryVariables(
            context.processingState().getEventScopeInstanceState(),
            context.processingState().getElementInstanceState());
  }
}
