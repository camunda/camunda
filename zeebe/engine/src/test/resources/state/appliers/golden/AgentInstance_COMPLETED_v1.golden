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
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;

public final class AgentInstanceCompletedApplier
    implements TypedEventApplier<AgentInstanceIntent, AgentInstanceRecord> {

  private final MutableAgentInstanceState agentInstanceState;
  private final MutableAgentHistoryState agentHistoryState;

  public AgentInstanceCompletedApplier(
      final MutableAgentInstanceState agentInstanceState,
      final MutableAgentHistoryState agentHistoryState) {
    this.agentInstanceState = agentInstanceState;
    this.agentHistoryState = agentHistoryState;
  }

  @Override
  public void applyState(final long key, final AgentInstanceRecord value) {
    agentInstanceState.delete(key, value);
    // The committed-ids map is retained for exactly the agent instance's lifetime; this is the
    // one point where that lifetime ends, and the only path that removes an agent instance at
    // all, so this is the single place cleanup needs to happen.
    agentHistoryState.deleteCommittedHistoryItemKeys(key);
  }
}
