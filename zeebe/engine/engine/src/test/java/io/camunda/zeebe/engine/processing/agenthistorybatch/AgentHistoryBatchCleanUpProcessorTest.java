/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistorybatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch.AgentHistoryBatchRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Unit-tests {@code AGENT_HISTORY_BATCH:CLEAN_UP} in isolation, with a small chunk size so the
 * chunking/deferral decisions can be exercised without needing hundreds of history items. The
 * actual removal of ids from state on {@code CLEANED} is covered separately by {@code
 * AgentHistoryBatchCleanedApplierTest}.
 */
@ExtendWith(ProcessingStateExtension.class)
public class AgentHistoryBatchCleanUpProcessorTest {

  private static final int CHUNK_SIZE = 2;
  private static final long AGENT_INSTANCE_KEY = 21L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentHistoryState agentHistoryState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private AgentHistoryBatchCleanUpProcessor processor;

  @BeforeEach
  void setup() {
    agentHistoryState = processingState.getAgentHistoryState();

    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    processor = new AgentHistoryBatchCleanUpProcessor(writers, processingState, CHUNK_SIZE);
  }

  @Test
  void shouldCleanUpCommittedHistoryItemIdWithinOneChunk() {
    // given — one committed history item, within a single chunk.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);

    // when
    processor.processRecord(cleanUpCommand());

    // then — a single CLEANED event lists exactly that id, and nothing is deferred.
    assertThat(capturedCleanedEvent().getHistoryItemIds()).containsExactly("item-1");
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldChunkCleanupWhenCommittedIdsExceedChunkSize() {
    // given — three committed history items, one more than the chunk size of two.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-2", 102L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-3", 103L);

    // when
    processor.processRecord(cleanUpCommand());

    // then — only chunkSize ids are cleaned in this cycle, and a follow-up CLEAN_UP is scheduled
    // to reach the one left over.
    assertThat(capturedCleanedEvent().getHistoryItemIds()).hasSize(CHUNK_SIZE);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(AGENT_INSTANCE_KEY),
            eq(AgentHistoryBatchIntent.CLEAN_UP),
            any(AgentHistoryBatchRecord.class));
  }

  @Test
  void shouldCombineCommittedAndMetricsAccumulatedIdsWithinOneChunk() {
    // given — one committed item and one discarded, metrics-accumulated-only item, together
    // filling the chunk of two.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);
    agentHistoryState.markMetricsAccumulated(AGENT_INSTANCE_KEY, "item-2");

    // when
    processor.processRecord(cleanUpCommand());

    // then — a single CLEANED event covers both ids: the metrics-accumulated-only one shares the
    // same chunk budget as the committed one instead of needing its own cycle.
    assertThat(capturedCleanedEvent().getHistoryItemIds())
        .containsExactlyInAnyOrder("item-1", "item-2");
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldDeferMetricsAccumulatedScanWhenCommittedAloneFillsChunk() {
    // given — three committed items (exceeding the chunk of two) plus one discarded,
    // metrics-accumulated-only item.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-2", 102L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-3", 103L);
    agentHistoryState.markMetricsAccumulated(AGENT_INSTANCE_KEY, "item-4");

    // when
    processor.processRecord(cleanUpCommand());

    // then — the chunk is sourced purely from the committed column family: reaching the chunk
    // size there defers the metrics-accumulated scan entirely rather than mixing in item-4, since
    // a follow-up cycle is already guaranteed once committed is exhausted.
    assertThat(capturedCleanedEvent().getHistoryItemIds())
        .hasSize(CHUNK_SIZE)
        .doesNotContain("item-4");
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(AGENT_INSTANCE_KEY),
            eq(AgentHistoryBatchIntent.CLEAN_UP),
            any(AgentHistoryBatchRecord.class));
  }

  @Test
  void shouldDeferDiscardOnlyItemToNextCycleWhenCommittedExactlyFillsChunk() {
    // given — exactly chunk-size committed items, plus one discard-only (metrics-accumulated
    // only) item. Since the committed column family has no id beyond the two it holds, visiting
    // it never probes past the chunk boundary, so hasMore stays false from committed alone and
    // the metrics-accumulated scan still runs within the same cycle.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-2", 102L);
    agentHistoryState.markMetricsAccumulated(AGENT_INSTANCE_KEY, "item-3");

    // when
    processor.processRecord(cleanUpCommand());

    // then — the discard-only item is the one that probes past the boundary during the
    // metrics-accumulated scan, so it's excluded from this cycle's chunk and deferred instead.
    assertThat(capturedCleanedEvent().getHistoryItemIds())
        .containsExactlyInAnyOrder("item-1", "item-2");
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(AGENT_INSTANCE_KEY),
            eq(AgentHistoryBatchIntent.CLEAN_UP),
            any(AgentHistoryBatchRecord.class));
  }

  @Test
  void shouldEmitCleanedEventWithEmptyListWhenNothingToCleanUp() {
    // given — no history items at all for this agent instance.

    // when
    processor.processRecord(cleanUpCommand());

    // then — a CLEANED event is still appended, with an empty list, so the command always leaves a
    // durable trace; no follow-up command is scheduled since there's nothing left to defer.
    assertThat(capturedCleanedEvent().getHistoryItemIds()).isEmpty();
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldNotTreatDuplicateIdAsNeedingNewBudgetSlot() {
    // given — two committed items exactly filling the chunk of two, one of which (item-1) is also
    // present in metrics-accumulated, plus a genuinely new metrics-accumulated-only item.
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-1", 101L);
    agentHistoryState.putCommittedHistoryItemKey(AGENT_INSTANCE_KEY, "item-2", 102L);
    agentHistoryState.markMetricsAccumulated(AGENT_INSTANCE_KEY, "item-1");
    agentHistoryState.markMetricsAccumulated(AGENT_INSTANCE_KEY, "item-3");

    // when
    processor.processRecord(cleanUpCommand());

    // then — re-encountering item-1 in the metrics-accumulated scan doesn't consume a budget slot
    // or falsely flag hasMore; the scan continues and still finds item-3 needs a follow-up cycle.
    assertThat(capturedCleanedEvent().getHistoryItemIds())
        .containsExactlyInAnyOrder("item-1", "item-2");
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(AGENT_INSTANCE_KEY),
            eq(AgentHistoryBatchIntent.CLEAN_UP),
            any(AgentHistoryBatchRecord.class));
  }

  private AgentHistoryBatchRecord capturedCleanedEvent() {
    final var captor = ArgumentCaptor.forClass(AgentHistoryBatchRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(AGENT_INSTANCE_KEY), eq(AgentHistoryBatchIntent.CLEANED), captor.capture());
    return captor.getValue();
  }

  private TypedRecord<AgentHistoryBatchRecord> cleanUpCommand() {
    return new UnwrittenRecord(
        AGENT_INSTANCE_KEY,
        1,
        new AgentHistoryBatchRecord().setAgentInstanceKey(AGENT_INSTANCE_KEY),
        new RecordMetadata());
  }
}
