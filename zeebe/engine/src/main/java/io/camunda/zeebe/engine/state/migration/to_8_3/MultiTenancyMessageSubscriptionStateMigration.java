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

public final class MultiTenancyMessageSubscriptionStateMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final ProcessingState processingState) {
    return hasOpenMessageSubscriptionsInDeprecatedCFs(processingState);
  }

  @Override
  public void runMigration(final MutableProcessingState processingState) {
    final var migrationState = processingState.getMigrationState();
    migrationState.migrateMessageEventSubscriptionForMultiTenancy();
  }

  private static boolean hasOpenMessageSubscriptionsInDeprecatedCFs(
      final ProcessingState processingState) {
    return !processingState.isEmpty(
        ZbColumnFamilies.DEPRECATED_MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY);
  }
}
