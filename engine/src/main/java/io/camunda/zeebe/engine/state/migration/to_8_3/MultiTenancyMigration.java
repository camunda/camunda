/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * This migration is used to extend the data with tenant information. Before this migration, no
 * tenant information was stored in the state. Therefore, we're considering all existing data to be
 * part of the default tenant.
 *
 * <p>This migration will set the tenant id of all existing data to the default tenant id. Both in
 * values and in keys. When the key has changed, the data is moved to a new column family.
 */
public class MultiTenancyMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  /**
   * We only need to run this migration if there is deployed processes, or deployed DRGs. If none of
   * these exist in the state, all the ColumnFamilies hit by the other migrations will be empty.
   */
  @Override
  public boolean needsToRun(final ProcessingState processingState) {
    final var hasDeployedProcesses =
        !processingState.isEmpty(ZbColumnFamilies.DEPRECATED_PROCESS_CACHE);
    final var hasDeployedDrgs =
        !processingState.isEmpty(ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS);

    return hasDeployedProcesses || hasDeployedDrgs;
  }

  @Override
  public void runMigration(final MutableProcessingState processingState) {
    final var migrationState = processingState.getMigrationState();
    migrationState.migrateMessageStartEventSubscriptionForMultiTenancy();
    migrationState.migrateMessageEventSubscriptionForMultiTenancy();
    migrationState.migrateProcessMessageSubscriptionForMultiTenancy();
    migrationState.migrateJobStateForMultiTenancy();
  }
}
