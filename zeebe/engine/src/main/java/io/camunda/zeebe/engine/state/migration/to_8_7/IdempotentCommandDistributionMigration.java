/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_7;

import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.concurrent.atomic.AtomicBoolean;

public class IdempotentCommandDistributionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    final var distributionState = context.processingState().getDistributionState();

    final var shouldRun = new AtomicBoolean(false);

    distributionState.foreachPendingDistribution(
        (distributionKey, distributionRecord) -> {
          final var valueType = distributionRecord.getValueType();
          final var isDeploymentOrDeletion =
              valueType == ValueType.DEPLOYMENT || valueType == ValueType.RESOURCE_DELETION;
          final var hasQueueId = distributionRecord.getQueueId();

          if (isDeploymentOrDeletion && hasQueueId == null) {
            shouldRun.set(true);
            return false;
          }

          return true;
        });

    return shouldRun.get();
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getMigrationState().migrateIdempotentCommandDistribution();
  }
}
