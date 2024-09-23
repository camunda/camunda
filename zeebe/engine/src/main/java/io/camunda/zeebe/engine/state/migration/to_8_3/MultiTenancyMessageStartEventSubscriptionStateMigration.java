/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class MultiTenancyMessageStartEventSubscriptionStateMigration
    implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return hasOpenMessageStartEventSubscriptionsInDeprecatedCFs(context.processingState());
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    final var migrationState = context.processingState().getMigrationState();
    migrationState.migrateMessageStartEventSubscriptionForMultiTenancy();
  }

  private static boolean hasOpenMessageStartEventSubscriptionsInDeprecatedCFs(
      final ProcessingState processingState) {
    return !processingState.isEmpty(
            ZbColumnFamilies.DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY)
        || !processingState.isEmpty(
            ZbColumnFamilies.DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME);
  }
}
