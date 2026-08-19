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

public final class AgentInstanceCreatedApplier
    implements TypedEventApplier<AgentInstanceIntent, AgentInstanceRecord> {

  private final MutableAgentInstanceState agentInstanceState;
  private final MutableElementInstanceState elementInstanceState;

  public AgentInstanceCreatedApplier(
      final MutableAgentInstanceState agentInstanceState,
      final MutableElementInstanceState elementInstanceState) {
    this.agentInstanceState = agentInstanceState;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long key, final AgentInstanceRecord value) {
    // A new field should join primary storage by default, since secondary-storage exporters
    // replace the whole entity with whatever the emitted event carries. `history`, `jobKey`, and
    // `jobLease` are the exceptions: history is already durably captured as its own AGENT_HISTORY
    // record and can grow large, so keeping a copy here would be wasteful; jobKey/jobLease are
    // only meaningful while the command that carries them is being processed, so persisting them
    // would just reflect whichever command happened to run last, not the instance's actual state.
    final var forStorage = new AgentInstanceRecord();
    forStorage.copyFrom(value);
    forStorage.setHistory(List.of()).setJobKey(-1L).setJobLease("");
    agentInstanceState.insert(key, forStorage);

    // Write back-link onto the parent ElementInstance so the CREATE processor can
    // detect a duplicate-CREATE against the same elementInstanceKey in O(1).
    final var elementInstance = elementInstanceState.getInstance(value.getElementInstanceKey());
    if (elementInstance != null) {
      elementInstance.setAgentInstanceKey(key);
      elementInstanceState.updateInstance(elementInstance);
    }
  }
}
