/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;

public final class RedistributionBehavior {
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public RedistributionBehavior(
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final ProcessingState processingState) {
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  public void startRedistribution(final long redistributionKey) {
    stateWriter.appendFollowUpEvent(
        redistributionKey, RedistributionIntent.STARTED, new RedistributionRecord());
    commandWriter.appendFollowUpCommand(
        redistributionKey, RedistributionIntent.COMPLETE, new RedistributionRecord());
  }
}
