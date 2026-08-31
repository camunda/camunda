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
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;

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
    // deleting the item needs the same two fields. historyItemId is kept too: dedup matches on it
    // once an item is pending or committed, so it must survive here as well. Storing the trimmed
    // copy also means the COMMITTED/DISCARDED events re-emitted from state carry only identity
    // fields, with no extra stripping needed at those emit sites.
    //
    // CONFIGURATION items are the exception: whichever of model/provider/systemPrompt/tools/limits
    // the item's own changedAttributes names must survive until COMMIT, since that's when
    // AgentHistoryCommitProcessor applies them onto the AgentInstance — so those fields (plus
    // changedAttributes itself) are also kept, but only for that role.
    //
    // This is an explicit allow-list (not a full copy with payload cleared afterward) so that a
    // field added to AgentHistoryRecord in the future is excluded from primary storage by default —
    // someone has to deliberately add it here for it to be persisted past CREATED. Keeping the
    // selection inline rather than in a shared helper also means any future change to which fields
    // are stored requires a new applier version with its own golden file, preventing silent
    // behavior changes to already-released appliers.
    final var storedRecord =
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
            .setHistoryItemId(value.getHistoryItemId())
            .setLoopIteration(value.getLoopIteration())
            .setRole(value.getRole());
    if (value.getRole() == AgentHistoryRole.CONFIGURATION) {
      final var changedAttributes = value.getChangedAttributes();
      storedRecord.setChangedAttributes(changedAttributes);

      if (changedAttributes.contains(AgentInstanceRecord.ATTR_MODEL)) {
        storedRecord.setModel(value.getModel());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_PROVIDER)) {
        storedRecord.setProvider(value.getProvider());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_SYSTEM_PROMPT)) {
        storedRecord.setSystemPrompt(value.getSystemPrompt());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_TOOLS)) {
        storedRecord.setTools(value.getTools());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_MAX_TOKENS)) {
        storedRecord.getLimits().setMaxTokens(value.getLimits().getMaxTokens());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_MAX_MODEL_CALLS)) {
        storedRecord.getLimits().setMaxModelCalls(value.getLimits().getMaxModelCalls());
      }
      if (changedAttributes.contains(AgentInstanceRecord.ATTR_MAX_TOOL_CALLS)) {
        storedRecord.getLimits().setMaxToolCalls(value.getLimits().getMaxToolCalls());
      }
    }
    agentHistoryState.insert(key, storedRecord);
  }
}
