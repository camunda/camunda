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
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;

public final class AgentHistoryCommittedApplier
    implements TypedEventApplier<AgentHistoryIntent, AgentHistoryRecord> {

  private final MutableAgentHistoryState agentHistoryState;
  private final MutableAgentInstanceState agentInstanceState;

  public AgentHistoryCommittedApplier(final MutableProcessingState processingState) {
    agentHistoryState = processingState.getAgentHistoryState();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  @Override
  public void applyState(final long key, final AgentHistoryRecord value) {
    if (value.getRole() == AgentHistoryRole.CONFIGURATION) {
      syncCommittedSnapshot(value.getAgentInstanceKey());
    }
    agentHistoryState.delete(key, value);
  }

  /**
   * Copies the current (already optimistically-applied) configuration fields of the live agent
   * instance into its committed snapshot, so a later discard can restore exactly this state.
   */
  private void syncCommittedSnapshot(final long agentInstanceKey) {
    final var live = agentInstanceState.getRecord(agentInstanceKey);
    final var snapshot = new AgentInstanceRecord();
    snapshot
        .getDefinition()
        .setModel(live.getDefinition().getModel())
        .setProvider(live.getDefinition().getProvider())
        .setSystemPrompt(live.getDefinition().getSystemPrompt());
    snapshot.setTools(live.getTools());
    final var liveLimits = live.getLimits();
    snapshot
        .getLimits()
        .setMaxTokens(liveLimits.getMaxTokens())
        .setMaxModelCalls(liveLimits.getMaxModelCalls())
        .setMaxToolCalls(liveLimits.getMaxToolCalls());
    agentInstanceState.putCommittedSnapshot(agentInstanceKey, snapshot);
  }
}
