/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.slf4j.Logger;

public final class RpaReexportMigrator implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final boolean enableRpaReexportMigration;
  private final ResourceState resourceState;

  public RpaReexportMigrator(
      final boolean enableRpaReexportMigration, final ResourceState resourceState) {
    this.enableRpaReexportMigration = enableRpaReexportMigration;
    this.resourceState = resourceState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // We can disable the migration by disabling the feature flag. This is useful to prevent
    // interference in our engine tests, as this setup will write "unexpected" commands/events
    if (!enableRpaReexportMigration) {
      return;
    }

    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be distributed to
      // the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping RPA reexport migration on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    if (resourceState.hasRanRpaReexportMigration()) {
      LOG.debug("RPA reexport migration already ran. Skipping.");
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              //              taskResultBuilder.appendCommandRecord(); TODO: append command
              return taskResultBuilder.build();
            });
  }
}
