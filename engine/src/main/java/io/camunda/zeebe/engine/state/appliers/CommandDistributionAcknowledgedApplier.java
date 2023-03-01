/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;

public final class CommandDistributionAcknowledgedApplier
    implements TypedEventApplier<CommandDistributionIntent, CommandDistributionRecord> {

  private final MutableDistributionState distributionState;

  public CommandDistributionAcknowledgedApplier(final MutableDistributionState distributionState) {
    this.distributionState = distributionState;
  }

  @Override
  public void applyState(final long key, final CommandDistributionRecord value) {
    distributionState.removePendingDistribution(key, value.getPartitionId());
  }
}
