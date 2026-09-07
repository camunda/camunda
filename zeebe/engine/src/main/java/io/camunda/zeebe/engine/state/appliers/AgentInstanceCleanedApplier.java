/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;

public final class AgentInstanceCleanedApplier
    implements TypedEventApplier<AgentInstanceIntent, AgentInstanceRecord> {

  private final MutableAgentHistoryState agentHistoryState;

  public AgentInstanceCleanedApplier(final MutableAgentHistoryState agentHistoryState) {
    this.agentHistoryState = agentHistoryState;
  }

  @Override
  public void applyState(final long key, final AgentInstanceRecord value) {
    for (final var historyItemId : value.getHistoryItemIdsToDelete()) {
      agentHistoryState.deleteCommittedHistoryItemKey(key, historyItemId);
      agentHistoryState.deleteMetricsAccumulatedId(key, historyItemId);
    }
  }
}
