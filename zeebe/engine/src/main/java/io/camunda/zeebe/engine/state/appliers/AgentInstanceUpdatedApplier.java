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
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import java.util.List;

public final class AgentInstanceUpdatedApplier
    implements TypedEventApplier<AgentInstanceIntent, AgentInstanceRecord> {

  private final MutableAgentInstanceState agentInstanceState;
  private final MutableElementInstanceState elementInstanceState;

  public AgentInstanceUpdatedApplier(
      final MutableAgentInstanceState agentInstanceState,
      final MutableElementInstanceState elementInstanceState) {
    this.agentInstanceState = agentInstanceState;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long key, final AgentInstanceRecord value) {
    // The secondary-storage exporters replace the whole entity with whatever the emitted event's
    // record contains, so primary storage must retain every field the secondary entity needs — a
    // new field should join it by default. `history`, `jobKey`, and `jobLease` are the deliberate
    // exceptions: they're per-command payload, not durable entity attributes, and everything they
    // carry is already persisted independently as its own AGENT_HISTORY record. Storing them here
    // too would be duplicative, and since only the most recent command's values are ever echoed
    // onto a given event, primary storage would end up reflecting "whatever the last command
    // happened to carry" rather than anything meaningful about the instance.
    final var forStorage = new AgentInstanceRecord();
    forStorage.copyFrom(value);
    forStorage.setHistory(List.of()).setJobKey(-1L).setJobLease("");
    agentInstanceState.update(key, forStorage);

    final var elementInstance = elementInstanceState.getInstance(value.getElementInstanceKey());
    if (elementInstance != null && elementInstance.getAgentInstanceKey() != key) {
      elementInstance.setAgentInstanceKey(key);
      elementInstanceState.updateInstance(elementInstance);
    }
  }
}
