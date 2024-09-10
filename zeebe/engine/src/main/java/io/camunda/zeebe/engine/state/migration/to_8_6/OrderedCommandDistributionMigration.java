/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_6;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class OrderedCommandDistributionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    final var processingState = context.processingState();

    /**
     * We need to move any pending distirbutions into the retriable distribution column family. We
     * only need to do this if we actually have pending distributions. To make sure we don't run the
     * migration twice we also check that the queued distribution column family is empty, and the
     * retriable distribution column family is empty.
     */
    return !processingState.isEmpty(ZbColumnFamilies.PENDING_DISTRIBUTION)
        && processingState.isEmpty(ZbColumnFamilies.QUEUED_DISTRIBUTION)
        && processingState.isEmpty(ZbColumnFamilies.RETRIABLE_DISTRIBUTION);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getMigrationState().migrateOrderedCommandDistribution();
  }
}
