/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;

/**
 * Applies the {@link AgentInstanceIntent#MIGRATED} event by re-pointing the agent instance at the
 * target process definition. Only the primary record is updated: process instance migration never
 * changes the {@code processInstanceKey} or {@code elementInstanceKey}, so neither the
 * process-instance secondary index nor the element-instance back-link needs maintenance.
 */
public final class AgentInstanceMigratedApplier
    implements TypedEventApplier<AgentInstanceIntent, AgentInstanceRecord> {

  private final MutableAgentInstanceState agentInstanceState;

  public AgentInstanceMigratedApplier(final MutableAgentInstanceState agentInstanceState) {
    this.agentInstanceState = agentInstanceState;
  }

  @Override
  public void applyState(final long key, final AgentInstanceRecord value) {
    agentInstanceState.update(key, value);
  }
}
