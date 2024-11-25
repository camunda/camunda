/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;

public final class RedistributionContinuedApplier
    implements TypedEventApplier<RedistributionIntent, RedistributionRecord> {
  private final MutableRedistributionState redistributionState;

  public RedistributionContinuedApplier(final MutableProcessingState processingState) {
    redistributionState = processingState.getRedistributionState();
  }

  @Override
  public void applyState(final long key, final RedistributionRecord value) {
    redistributionState.updateState(
        RedistributionStage.indexToStage(value.getStage()), value.getProgress());
  }
}
