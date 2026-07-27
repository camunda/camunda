/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.camunda.zeebe.engine.state.immutable.OrdinalActiveState;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;

public class OrdinalRolloverScheduler implements StreamProcessorLifecycleAware {
  private final OrdinalActiveState ordinalActiveState;
  private final Duration schedulerInterval;

  public OrdinalRolloverScheduler(
      final OrdinalActiveState ordinalActiveState, final Duration schedulerInterval) {
    this.ordinalActiveState = ordinalActiveState;
    this.schedulerInterval = schedulerInterval;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != DEPLOYMENT_PARTITION) {
      return;
    }

    context.getScheduleService().runAtFixedRate(schedulerInterval, this::runRollover);
  }

  private TaskResult runRollover(final TaskResultBuilder taskResultBuilder) {
    final int current = ordinalActiveState.getActiveOrdinalKey();
    final var record = new OrdinalRecord().setOrdinalKey(current + 1);
    taskResultBuilder.appendCommandRecord(OrdinalIntent.ACTIVATE, record);
    return taskResultBuilder.build();
  }
}
