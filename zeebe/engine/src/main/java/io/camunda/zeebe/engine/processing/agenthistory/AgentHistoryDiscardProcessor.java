/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

// DISCARD is always a follow-up command emitted internally by the engine when an agentic job is
// destroyed without completing, so there is no user to authorize against.
@ExcludeAuthorizationCheck
public final class AgentHistoryDiscardProcessor
    implements TypedRecordProcessor<AgentHistoryRecord> {

  private final StateWriter stateWriter;
  private final AgentHistoryState agentHistoryState;

  public AgentHistoryDiscardProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    agentHistoryState = processingState.getAgentHistoryState();
  }

  @Override
  public void processRecord(final TypedRecord<AgentHistoryRecord> command) {
    final long jobKey = command.getValue().getJobKey();
    final String jobLease = command.getValue().getJobLease();
    // Items in state are already trimmed to identity fields by AgentHistoryCreatedApplier,
    // so the DISCARDED event emitted here carries that same trimmed shape for free.
    final AgentHistoryState.AgentHistoryVisitor visitor =
        item ->
            stateWriter.appendFollowUpEvent(
                item.getAgentHistoryKey(), AgentHistoryIntent.DISCARDED, item);
    if (jobLease.isEmpty()) {
      // Job destruction: every activation's items are dead — discard all items for the job.
      agentHistoryState.visitByJobKey(jobKey, visitor);
    } else {
      // Supersession: only the given (dead) activation's items are discarded.
      agentHistoryState.visitByJobLease(jobKey, jobLease, visitor);
    }
    // no-op when no items exist — backward-compatible with non-agentic jobs
  }
}
