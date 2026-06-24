/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Migrates pending process message subscriptions by adding them to {@code
 * PendingProcessMessageSubscriptionState} and removing them from {@code
 * ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME}.
 */
public class ProcessMessageSubscriptionSentTimeMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return ProcessMessageSubscriptionSentTimeMigration.class.getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return !context.processingState().isEmpty(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context
        .processingState()
        .getMigrationState()
        .migrateProcessMessageSubscriptionSentTime(
            context.processingState().getProcessMessageSubscriptionState(),
            context.processingState().getPendingProcessMessageSubscriptionState());
  }
}
