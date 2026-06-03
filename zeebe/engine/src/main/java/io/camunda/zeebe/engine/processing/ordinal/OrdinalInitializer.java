/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.state.immutable.OrdinalState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

public final class OrdinalInitializer implements StreamProcessorLifecycleAware {

  private final OrdinalState ordinalState;

  public OrdinalInitializer(final OrdinalState ordinalState) {
    this.ordinalState = ordinalState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      return;
    }

    if (ordinalState.getActiveOrdinalKey() > 0) {
      return;
    }

    // TODO: @yohanfernando >> flesh out actual initialisation
    final var record = new OrdinalRecord().setOrdinalKey(1);
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              taskResultBuilder.appendCommandRecord(OrdinalIntent.ACTIVATE, record);
              return taskResultBuilder.build();
            });
  }
}
