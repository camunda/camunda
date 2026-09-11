/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_8;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;

/**
 * Promotes process definitions that are durably stuck in {@code PENDING_DELETION} back to {@code
 * ACTIVE}. See {@link DbProcessReactivateMigrationState} for why a resting {@code PENDING_DELETION}
 * is always an artifact of the {@code PersistedProcess.wrap} bug (#62820; fixed in #59319), and
 * therefore safe to reactivate unconditionally.
 */
public final class ReactivatePendingDeletionProcessesMigration implements MigrationTask {

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
    context.processingState().getMigrationState().reactivatePendingDeletionProcesses();
  }
}
