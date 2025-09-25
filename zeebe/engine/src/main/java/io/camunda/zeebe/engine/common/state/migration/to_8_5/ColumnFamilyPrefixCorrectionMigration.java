/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.migration.to_8_5;

import io.camunda.zeebe.engine.common.state.migration.MigrationTask;
import io.camunda.zeebe.engine.common.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.common.state.migration.MutableMigrationTaskContext;

public class ColumnFamilyPrefixCorrectionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return true;
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    final var migrationState = context.processingState().getMigrationState();
    migrationState.correctColumnFamilyPrefix();
  }
}
