/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessDefinitionRestoreRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.slf4j.Logger;

/**
 * Schedules the one-time startup cleanup that restores stray resting {@code PENDING_DELETION}
 * process definitions left behind by a write-side bug back to {@code ACTIVE}.
 *
 * <p>Unlike {@link RpaReexportMigrator}, which only runs on the deployment partition and
 * distributes its work, this runs on every partition's leader independently: the stray rows can
 * exist on any partition, process definitions are not scoped to the deployment partition, and there
 * is no need for a single coordinator since each partition only ever touches its own state.
 */
public final class ProcessDefinitionRestoreTrigger implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final boolean enableProcessDefinitionRestore;
  private final ProcessState processState;

  public ProcessDefinitionRestoreTrigger(
      final boolean enableProcessDefinitionRestore, final ProcessState processState) {
    this.enableProcessDefinitionRestore = enableProcessDefinitionRestore;
    this.processState = processState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // We can disable the restore by disabling the feature flag. This is useful to prevent
    // interference in our engine tests, as this setup will write "unexpected" commands/events
    if (!enableProcessDefinitionRestore) {
      return;
    }

    if (processState.hasRunProcessDefinitionRestore()) {
      LOG.debug(
          "Process definition restore already ran on partition {}. Skipping.",
          context.getPartitionId());
      return;
    }

    // We use a timestamp of 0L to ensure this runs immediately once the stream processor is
    // started
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              taskResultBuilder.appendCommandRecord(
                  ProcessDefinitionRestoreIntent.START, new ProcessDefinitionRestoreRecord());
              return taskResultBuilder.build();
            });
  }
}
