/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAgentDefinitionState;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;

public final class AgentDefinitionDeletedApplier
    implements TypedEventApplier<AgentDefinitionIntent, AgentDefinitionRecord> {

  private final MutableAgentDefinitionState agentDefinitionState;

  public AgentDefinitionDeletedApplier(final MutableAgentDefinitionState agentDefinitionState) {
    this.agentDefinitionState = agentDefinitionState;
  }

  @Override
  public void applyState(final long key, final AgentDefinitionRecord value) {
    agentDefinitionState.delete(value);
  }
}
