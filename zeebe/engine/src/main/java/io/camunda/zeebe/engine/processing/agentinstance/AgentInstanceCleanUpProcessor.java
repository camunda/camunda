/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles {@code AGENT_INSTANCE:CLEAN_UP}: deletes up to {@code chunkSize} distinct committed/
 * metrics-accumulated history-item ids recorded for one already-completed agent instance,
 * accumulating both sources into a single shared set bounded by {@code chunkSize}. The same id is
 * commonly present in both column families, so sharing one budget across them — rather than
 * splitting the budget in half up front — finds as many distinct ids per cycle as the chunk size
 * allows instead of wasting part of the budget on ids already found. Re-appends the same command as
 * a follow-up if either source was cut off before it was fully visited. Once nothing remains, no
 * {@code CLEANED} event is written and the chain stops.
 */
@ExcludeAuthorizationCheck
public final class AgentInstanceCleanUpProcessor
    implements TypedRecordProcessor<AgentInstanceRecord>, SuspensionAware<AgentInstanceRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final AgentHistoryState agentHistoryState;
  private final int chunkSize;

  public AgentInstanceCleanUpProcessor(
      final Writers writers, final ProcessingState processingState, final int chunkSize) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    agentHistoryState = processingState.getAgentHistoryState();
    this.chunkSize = chunkSize;
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final long agentInstanceKey = command.getKey();

    final var idsToDelete = new HashSet<String>();
    final var hasMore = new AtomicBoolean(false);

    agentHistoryState.visitCommittedHistoryItemIds(
        agentInstanceKey, id -> collectUpToChunkSize(idsToDelete, hasMore, id));
    if (idsToDelete.size() < chunkSize) {
      agentHistoryState.visitMetricsAccumulatedHistoryItemIds(
          agentInstanceKey, id -> collectUpToChunkSize(idsToDelete, hasMore, id));
    }

    if (idsToDelete.isEmpty()) {
      return;
    }

    stateWriter.appendFollowUpEvent(
        agentInstanceKey,
        AgentInstanceIntent.CLEANED,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setHistoryItemIdsToDelete(idsToDelete.stream().toList()));

    if (hasMore.get()) {
      commandWriter.appendFollowUpCommand(
          agentInstanceKey,
          AgentInstanceIntent.CLEAN_UP,
          new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey));
    }
  }

  private boolean collectUpToChunkSize(
      final HashSet<String> idsToDelete, final AtomicBoolean hasMore, final String id) {
    idsToDelete.add(id);
    if (idsToDelete.size() < chunkSize) {
      return true;
    }
    hasMore.set(true);
    return false;
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentInstanceRecord> record) {
    return SuspensionBehavior.PROCESS;
  }
}
