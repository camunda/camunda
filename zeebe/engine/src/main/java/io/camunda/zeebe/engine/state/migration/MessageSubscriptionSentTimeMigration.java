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
 * Reads out the sent time for message subscriptions and sets the {@code correlating} field in
 * records in{@code ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY}
 */
public class MessageSubscriptionSentTimeMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return MessageSubscriptionSentTimeMigration.class.getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    return !context.processingState().isEmpty(ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME);
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context
        .processingState()
        .getMigrationState()
        .migrateMessageSubscriptionSentTime(
            context.processingState().getMessageSubscriptionState(),
            context.processingState().getPendingMessageSubscriptionState());
  }
}
