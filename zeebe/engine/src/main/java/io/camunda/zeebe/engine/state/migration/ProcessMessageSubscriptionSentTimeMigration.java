/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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
  public boolean needsToRun(final ProcessingState processingState) {
    return !processingState.isEmpty(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME);
  }

  @Override
  public void runMigration(final MutableProcessingState processingState) {
    processingState
        .getMigrationState()
        .migrateProcessMessageSubscriptionSentTime(
            processingState.getProcessMessageSubscriptionState(),
            processingState.getPendingProcessMessageSubscriptionState());
  }
}
