/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.agentinstance.AgentHistoryBatchBehavior;
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
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

// COMMIT is always a follow-up command emitted internally by the engine and is never issued
// through the public API, so there is no user to authorize against.
@ExcludeAuthorizationCheck
public final class AgentHistoryCommitProcessor
    implements TypedRecordProcessor<AgentHistoryRecord>, SuspensionAware<AgentHistoryRecord> {

  private static final Logger LOG = Loggers.ENGINE_PROCESSING_LOGGER;

  private final StateWriter stateWriter;
  private final AgentInstanceState agentInstanceState;
  private final AgentHistoryState agentHistoryState;

  public AgentHistoryCommitProcessor(final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    agentInstanceState = processingState.getAgentInstanceState();
    agentHistoryState = processingState.getAgentHistoryState();
  }

  @Override
  public void processRecord(final TypedRecord<AgentHistoryRecord> command) {
    final long jobKey = command.getValue().getJobKey();
    final String jobLease = command.getValue().getJobLease();

    // The agent instance record is only needed if a CONFIGURATION item is committed, so we lazily
    // load it only if needed.
    final AtomicReference<AgentInstanceRecord> agentInstance = new AtomicReference<>();

    if (jobLease.isEmpty()) {
      agentHistoryState.visitByJobKey(jobKey, item -> commitHistoryItem(item, agentInstance));
    } else {
      agentHistoryState.visitByJobLease(
          jobKey, jobLease, item -> commitHistoryItem(item, agentInstance));
      // Discard items from superseded activations (different lease, same job)
      agentHistoryState.visitByJobKey(
          jobKey,
          item -> {
            if (!jobLease.equals(item.getJobLease())) {
              stateWriter.appendFollowUpEvent(
                  item.getAgentHistoryKey(), AgentHistoryIntent.DISCARDED, item);
            }
          });
    }
    // no-op when no items exist — backward-compatible with non-agentic jobs
  }

  private void commitHistoryItem(
      final AgentHistoryRecord item, final AtomicReference<AgentInstanceRecord> agentInstance) {
    // Items in state are already trimmed down by AgentHistoryCreatedApplier (identity fields
    // only, plus the CONFIGURATION-specific fields below for that role), so the
    // COMMITTED/DISCARDED events emitted here carry that same trimmed shape for free.
    stateWriter.appendFollowUpEvent(item.getAgentHistoryKey(), AgentHistoryIntent.COMMITTED, item);

    // When committing a CONFIGURATION item, the Agent Instance itself is updated with the new
    // configuration
    if (item.getRole() == AgentHistoryRole.CONFIGURATION) {
      // Lazily load agent instance record
      if (agentInstance.get() == null) {
        agentInstance.set(agentInstanceState.getRecord(item.getAgentInstanceKey()));
      }

      // Safety net, should never happen: a CONFIGURATION item's job is always tied to exactly one
      // still-existing agent instance. Guards against an NPE below if that ever stops holding.
      if (agentInstance.get() == null) {
        LOG.error(
            "Expected agent instance '{}' to exist while committing agent history item '{}', but"
                + " it was not found",
            item.getAgentInstanceKey(),
            item.getAgentHistoryKey());
        return;
      }

      // Apply configuration changes and emit UPDATED event
      final var changedAttributes =
          AgentHistoryBatchBehavior.applyConfigurationChanges(agentInstance.get(), item);
      agentInstance.get().setChangedAttributes(changedAttributes.stream().sorted().toList());
      stateWriter.appendFollowUpEvent(
          agentInstance.get().getAgentInstanceKey(),
          AgentInstanceIntent.UPDATED,
          agentInstance.get());
    }
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentHistoryRecord> record) {
    return SuspensionBehavior.PROCESS;
  }
}
