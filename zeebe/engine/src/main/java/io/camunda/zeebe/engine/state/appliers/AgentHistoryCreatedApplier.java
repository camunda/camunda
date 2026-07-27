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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;

public final class AgentHistoryCreatedApplier
    implements TypedEventApplier<AgentHistoryIntent, AgentHistoryRecord> {

  private final MutableAgentHistoryState agentHistoryState;

  public AgentHistoryCreatedApplier(final MutableProcessingState processingState) {
    agentHistoryState = processingState.getAgentHistoryState();
  }

  @Override
  public void applyState(final long key, final AgentHistoryRecord value) {
    // Store only the identity fields in primary storage (RocksDB). content/toolCalls/metrics/
    // producedAt have already reached secondary storage via the CREATED event itself; nothing reads
    // them back out of primary storage — matching a COMMIT/DISCARD only needs jobKey/jobLease, and
    // deleting the item needs the same two fields. Storing the trimmed copy also means the
    // COMMITTED/DISCARDED events re-emitted from state carry only identity fields, with no extra
    // stripping needed at those emit sites.
    //
    // This is an explicit allow-list (not a full copy with payload cleared afterward) so that a
    // field added to AgentHistoryRecord in the future is excluded from primary storage by default —
    // someone has to deliberately add it here for it to be persisted past CREATED. Keeping the
    // selection inline rather than in a shared helper also means any future change to which fields
    // are stored requires a new applier version with its own golden file, preventing silent
    // behavior
    // changes to already-released appliers.
    final var identityRecord =
        new AgentHistoryRecord()
            .setAgentHistoryKey(value.getAgentHistoryKey())
            .setAgentInstanceKey(value.getAgentInstanceKey())
            .setElementInstanceKey(value.getElementInstanceKey())
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setRootProcessInstanceKey(value.getRootProcessInstanceKey())
            .setBpmnProcessId(value.getBpmnProcessId())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setTenantId(value.getTenantId())
            .setJobKey(value.getJobKey())
            .setJobLease(value.getJobLease())
            .setLoopIteration(value.getLoopIteration())
            .setRole(value.getRole());
    agentHistoryState.insert(key, identityRecord);
  }
}
