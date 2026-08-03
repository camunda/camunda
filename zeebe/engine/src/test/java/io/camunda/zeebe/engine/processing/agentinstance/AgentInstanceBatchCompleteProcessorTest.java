/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
final class AgentInstanceBatchCompleteProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 10L;
  private static final long PROCESS_DEFINITION_KEY = 20L;
  private static final long NEXT_BATCH_KEY = 999L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private KeyGenerator keyGenerator;

  @BeforeEach
  void setUp() {
    agentInstanceState = processingState.getAgentInstanceState();

    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    when(commandWriter.canWriteCommandOfLength(anyInt())).thenReturn(true);
    keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(NEXT_BATCH_KEY);
  }

  private AgentInstanceBatchCompleteProcessor createProcessor(final int batchLimit) {
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    return new AgentInstanceBatchCompleteProcessor(
        writers, keyGenerator, agentInstanceState, batchLimit);
  }

  private void insertAgentInstance(final long agentInstanceKey, final long processInstanceKey) {
    agentInstanceState.insert(
        agentInstanceKey, new AgentInstanceRecord().setProcessInstanceKey(processInstanceKey));
  }

  private MockTypedRecord<AgentInstanceBatchRecord> command(
      final long key, final AgentInstanceBatchRecord value) {
    return new MockTypedRecord<>(key, new RecordMetadata(), value);
  }

  @Test
  void shouldCompleteEveryVisitedAgentInstanceUpToTheLimit() {
    // given
    insertAgentInstance(1L, PROCESS_INSTANCE_KEY);
    insertAgentInstance(2L, PROCESS_INSTANCE_KEY);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then
    verify(commandWriter).appendFollowUpCommand(eq(1L), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter).appendFollowUpCommand(eq(2L), eq(AgentInstanceIntent.COMPLETE), any());
  }

  @Test
  void shouldWriteCompletedEventWhenEveryAgentInstanceHasBeenVisited() {
    // given
    insertAgentInstance(1L, PROCESS_INSTANCE_KEY);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then
    verify(stateWriter)
        .appendFollowUpEvent(eq(500L), eq(AgentInstanceBatchIntent.COMPLETED), eq(value));
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(AgentInstanceBatchIntent.COMPLETE), any());
  }

  @Test
  void shouldRescheduleWithResumeCursorWhenBatchLimitIsReached() {
    // given - three agent instances but the batch limit only allows two per cycle
    insertAgentInstance(1L, PROCESS_INSTANCE_KEY);
    insertAgentInstance(2L, PROCESS_INSTANCE_KEY);
    insertAgentInstance(3L, PROCESS_INSTANCE_KEY);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    // when
    createProcessor(2).processRecord(command(500L, value));

    // then - only the first two agent instances are completed in this cycle
    verify(commandWriter).appendFollowUpCommand(eq(1L), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter).appendFollowUpCommand(eq(2L), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter, never())
        .appendFollowUpCommand(eq(3L), eq(AgentInstanceIntent.COMPLETE), any());

    // and - a follow-up batch command resumes from the last visited key with a fresh key
    final var followUpCaptor = ArgumentCaptor.forClass(AgentInstanceBatchRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(NEXT_BATCH_KEY), eq(AgentInstanceBatchIntent.COMPLETE), followUpCaptor.capture());
    final var followUp = followUpCaptor.getValue();
    assertThat(followUp.getProcessInstanceKey())
        .as("the follow-up batch command must keep completing the same process instance")
        .isEqualTo(PROCESS_INSTANCE_KEY);
    assertThat(followUp.getProcessDefinitionKey())
        .as("the follow-up batch command must carry the process definition key forward unchanged")
        .isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(followUp.getAgentInstanceKey())
        .as("the follow-up batch command must resume from the last completed agent instance key")
        .isEqualTo(2L);

    // and - no terminal COMPLETED event is written while agent instances remain
    verify(stateWriter, never())
        .appendFollowUpEvent(anyLong(), eq(AgentInstanceBatchIntent.COMPLETED), any());
  }

  @Test
  void shouldRescheduleWhenCommandWriterHasNoCapacityForTheNextCommand() {
    // given - two agent instances, but the command writer runs out of space after the first
    insertAgentInstance(1L, PROCESS_INSTANCE_KEY);
    insertAgentInstance(2L, PROCESS_INSTANCE_KEY);
    when(commandWriter.canWriteCommandOfLength(anyInt())).thenReturn(true, false);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then
    verify(commandWriter, times(1))
        .appendFollowUpCommand(anyLong(), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter).appendFollowUpCommand(eq(1L), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter)
        .appendFollowUpCommand(eq(NEXT_BATCH_KEY), eq(AgentInstanceBatchIntent.COMPLETE), any());
  }

  @Test
  void shouldReserveCapacityForTheFollowUpBatchCommandWhenCheckingCapacity() {
    // given - the follow-up AGENT_INSTANCE_BATCH:COMPLETE command must always fit, even if the
    // record batch is otherwise full right after the last visited agent instance
    insertAgentInstance(1L, PROCESS_INSTANCE_KEY);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    final var agentInstanceCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(1L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var followUpBatchCommandWorstCaseLength =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(Long.MAX_VALUE)
            .setProcessDefinitionKey(Long.MAX_VALUE)
            .setAgentInstanceKey(Long.MAX_VALUE)
            .getLength();

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then - the checked length reserves room for the self-chaining follow-up command on top of
    // the agent instance command itself
    verify(commandWriter)
        .canWriteCommandOfLength(
            agentInstanceCommand.getLength() + followUpBatchCommandWorstCaseLength);
  }

  @Test
  void shouldWriteCompletedEventWhenProcessInstanceHasNoAgentInstances() {
    // given - no agent instances stored for this process instance
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(AgentInstanceBatchIntent.COMPLETE), any());
    verify(stateWriter)
        .appendFollowUpEvent(eq(500L), eq(AgentInstanceBatchIntent.COMPLETED), eq(value));
  }

  @Test
  void shouldResumeFromTheCursorCarriedByTheBatchCommand() {
    // given - agent instance 1 was already completed in a previous cycle: its AGENT_INSTANCE:
    // COMPLETE command was processed and AgentInstanceCompletedApplier deleted it from state
    // before this follow-up AGENT_INSTANCE_BATCH:COMPLETE command could be processed, so only
    // agent instance 2 remains for this cycle to visit
    insertAgentInstance(2L, PROCESS_INSTANCE_KEY);
    final var value =
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setAgentInstanceKey(1L);

    // when
    createProcessor(100).processRecord(command(500L, value));

    // then - iteration resumes just past the already-deleted cursor, at agent instance 2
    verify(commandWriter).appendFollowUpCommand(eq(2L), eq(AgentInstanceIntent.COMPLETE), any());
    verify(commandWriter, never())
        .appendFollowUpCommand(eq(1L), eq(AgentInstanceIntent.COMPLETE), any());
  }
}
