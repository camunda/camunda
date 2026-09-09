/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistorybatch;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch.AgentHistoryBatchRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles {@code AGENT_HISTORY_BATCH:CLEAN_UP}: deletes up to {@code chunkSize} distinct committed/
 * metrics-accumulated history-item ids recorded for one already-completed agent instance,
 * accumulating both sources into a single shared set bounded by {@code chunkSize}. The same id is
 * commonly present in both column families, so sharing one budget across them — rather than
 * splitting the budget in half up front — finds as many distinct ids per cycle as the chunk size
 * allows instead of wasting part of the budget on ids already found. Re-appends the same command as
 * a follow-up if either source was cut off before it was fully visited. A {@code CLEANED} event is
 * always appended, even with an empty {@code historyItemIds} list, so the command always leaves a
 * durable trace; the chain itself stops once nothing remains to defer.
 */
@ExcludeAuthorizationCheck
public final class AgentHistoryBatchCleanUpProcessor
    implements TypedRecordProcessor<AgentHistoryBatchRecord>,
        SuspensionAware<AgentHistoryBatchRecord> {

  /**
   * Number of history-item ids deleted, combined across the committed and metrics-accumulated
   * indexes, per {@code AGENT_HISTORY_BATCH:CLEANED} cycle. Each history-item id is capped at 256
   * characters, so this bound keeps a cycle's worst case around 256 KB — comfortably within the
   * default max message size, and smaller configured ones too.
   */
  public static final int CHUNK_SIZE = 1000;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final AgentHistoryState agentHistoryState;
  private final int chunkSize;

  public AgentHistoryBatchCleanUpProcessor(
      final Writers writers, final ProcessingState processingState) {
    this(writers, processingState, CHUNK_SIZE);
  }

  public AgentHistoryBatchCleanUpProcessor(
      final Writers writers, final ProcessingState processingState, final int chunkSize) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    agentHistoryState = processingState.getAgentHistoryState();
    this.chunkSize = chunkSize;
  }

  @Override
  public void processRecord(final TypedRecord<AgentHistoryBatchRecord> command) {
    final long agentInstanceKey = command.getKey();

    final var idsToDelete = new HashSet<String>();
    final var hasMore = new AtomicBoolean(false);

    agentHistoryState.visitCommittedHistoryItemIds(
        agentInstanceKey, id -> collectUpToChunkSize(idsToDelete, hasMore, id));
    // Only skip the metrics-accumulated scan when committed already left more work queued for a
    // follow-up cycle — that cycle will reach metrics once committed is exhausted. If committed
    // came up empty-handed (hasMore false), metrics must still be probed even when the budget is
    // already full from committed alone: otherwise an id present only in metrics-accumulated
    // (e.g. a discarded item, never committed) would never be visited, and its entry would never
    // get cleaned up.
    if (!hasMore.get()) {
      agentHistoryState.visitMetricsAccumulatedHistoryItemIds(
          agentInstanceKey, id -> collectUpToChunkSize(idsToDelete, hasMore, id));
    }

    stateWriter.appendFollowUpEvent(
        agentInstanceKey,
        AgentHistoryBatchIntent.CLEANED,
        new AgentHistoryBatchRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setHistoryItemIds(idsToDelete.stream().toList()));

    if (hasMore.get()) {
      commandWriter.appendFollowUpCommand(
          agentInstanceKey,
          AgentHistoryBatchIntent.CLEAN_UP,
          new AgentHistoryBatchRecord().setAgentInstanceKey(agentInstanceKey));
    }
  }

  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  private boolean collectUpToChunkSize(
      final HashSet<String> idsToDelete, final AtomicBoolean hasMore, final String id) {
    // An id already collected from the other column family doesn't need a new budget slot: check
    // for it before the chunk-full check below, so a duplicate never falsely triggers hasMore and
    // never stops a scan that could still find a genuinely new id.
    if (idsToDelete.contains(id)) {
      return true;
    }
    // Probe one id past the chunk boundary rather than stopping the instant the boundary is
    // reached: only being called again after filling the budget proves another id genuinely
    // exists, so an exact-chunkSize total correctly leaves hasMore false instead of scheduling a
    // follow-up cycle that would find nothing.
    if (idsToDelete.size() == chunkSize) {
      hasMore.set(true);
      return false;
    }
    idsToDelete.add(id);
    return true;
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<AgentHistoryBatchRecord> record) {
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<AgentHistoryBatchRecord> record) {
    return SuspensionAction.PROCESS;
  }
}
