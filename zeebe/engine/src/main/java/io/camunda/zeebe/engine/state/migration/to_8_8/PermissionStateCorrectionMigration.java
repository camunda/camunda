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
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class PermissionStateCorrectionMigration implements MigrationTask {
  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    final var processingState = context.processingState();

    /*
     * We need to correct any instances where a permission does not exist in the permissions CF but
     * does exist in a record in the authorizations CF under permissionType. We only need to do this
     * if we have authorizations present.
     */
    return !processingState.isEmpty(ZbColumnFamilies.AUTHORIZATIONS);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getMigrationState().migrateMissingPermissionsForAuthorizations();
  }
}
