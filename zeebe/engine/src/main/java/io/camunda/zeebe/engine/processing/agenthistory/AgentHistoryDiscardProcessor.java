/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

// DISCARD is always a follow-up command emitted internally by the engine when an agentic job is
// destroyed without completing, so there is no user to authorize against.
@ExcludeAuthorizationCheck
public final class AgentHistoryDiscardProcessor
    implements TypedRecordProcessor<AgentHistoryRecord>, SuspensionAware<AgentHistoryRecord> {

  private static final List<String> RESTORED_ATTRIBUTES =
      List.of(
          AgentInstanceRecord.ATTR_MODEL,
          AgentInstanceRecord.ATTR_PROVIDER,
          AgentInstanceRecord.ATTR_SYSTEM_PROMPT,
          AgentInstanceRecord.ATTR_TOOLS,
          AgentInstanceRecord.ATTR_MAX_TOKENS,
          AgentInstanceRecord.ATTR_MAX_MODEL_CALLS,
          AgentInstanceRecord.ATTR_MAX_TOOL_CALLS);

  private final StateWriter stateWriter;
  private final AgentHistoryState agentHistoryState;
  private final AgentInstanceState agentInstanceState;

  public AgentHistoryDiscardProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    agentHistoryState = processingState.getAgentHistoryState();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  @Override
  public void processRecord(final TypedRecord<AgentHistoryRecord> command) {
    final long jobKey = command.getValue().getJobKey();
    final String jobLease = command.getValue().getJobLease();
    // Items in state are already trimmed to identity fields by AgentHistoryCreatedApplier,
    // so the DISCARDED event emitted here carries that same trimmed shape for free.
    final AgentHistoryState.AgentHistoryVisitor visitor =
        item -> {
          stateWriter.appendFollowUpEvent(
              item.getAgentHistoryKey(), AgentHistoryIntent.DISCARDED, item);
          if (item.getRole() == AgentHistoryRole.CONFIGURATION) {
            restoreConfigurationFromSnapshot(item.getAgentInstanceKey());
          }
        };
    if (jobLease.isEmpty()) {
      // Job destruction: every activation's items are dead — discard all items for the job.
      agentHistoryState.visitByJobKey(jobKey, visitor);
    } else {
      // Supersession: only the given (dead) activation's items are discarded.
      agentHistoryState.visitByJobLease(jobKey, jobLease, visitor);
    }
    // no-op when no items exist — backward-compatible with non-agentic jobs
  }

  /**
   * Unconditionally overwrites {@code agentInstanceKey}'s live configuration fields (model,
   * provider, systemPrompt, tools, limits) with its last committed snapshot, undoing whatever the
   * now-discarded CONFIGURATION item had optimistically applied. If no snapshot exists yet (no
   * CONFIGURATION item for this agent instance has ever committed), it rolls back to the {@link
   * AgentInstanceRecord} defaults instead. A no-op if the agent instance itself is already gone.
   */
  private void restoreConfigurationFromSnapshot(final long agentInstanceKey) {
    final var live = agentInstanceState.getRecord(agentInstanceKey);
    if (live == null) {
      return;
    }
    final var snapshot = agentInstanceState.getCommittedSnapshot(agentInstanceKey);
    final var restored = snapshot != null ? snapshot : new AgentInstanceRecord();

    final var liveDefinition = live.getDefinition();
    final var restoredDefinition = restored.getDefinition();
    liveDefinition
        .setModel(restoredDefinition.getModel())
        .setProvider(restoredDefinition.getProvider())
        .setSystemPrompt(restoredDefinition.getSystemPrompt());
    live.setTools(restored.getTools());
    final var restoredLimits = restored.getLimits();
    live.getLimits()
        .setMaxTokens(restoredLimits.getMaxTokens())
        .setMaxModelCalls(restoredLimits.getMaxModelCalls())
        .setMaxToolCalls(restoredLimits.getMaxToolCalls());

    live.setChangedAttributes(RESTORED_ATTRIBUTES);
    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.UPDATED, live);
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentHistoryRecord> record) {
    return SuspensionBehavior.PROCESS;
  }
}
