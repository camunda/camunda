/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(ProcessingStateExtension.class)
public final class SuspensionStateTest {

  private MutableProcessingState processingState;
  private MutableSuspensionState suspensionState;

  @BeforeEach
  public void setup() {
    suspensionState = processingState.getSuspensionState();
  }

  @Test
  public void shouldReturnNullSuspensionStateWhenAbsent() {
    // given
    final long processInstanceKey = 1L;

    // when
    final var state = suspensionState.getSuspensionState(processInstanceKey);

    // then
    assertThat(state).isNull();
  }

  @ParameterizedTest
  @EnumSource(State.class)
  public void shouldSetAndGetSuspensionState(final State state) {
    // given
    final long processInstanceKey = 1L;

    // when
    suspensionState.setSuspensionState(processInstanceKey, state);

    // then
    assertThat(suspensionState.getSuspensionState(processInstanceKey)).isEqualTo(state);
    assertThat(suspensionState.isSuspended(processInstanceKey)).isTrue();
  }

  @Test
  public void shouldOverwriteSuspensionStateWhenSetTwice() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.setSuspensionState(processInstanceKey, State.SUSPENDED);

    // when
    suspensionState.setSuspensionState(processInstanceKey, State.RESUMING);

    // then
    assertThat(suspensionState.getSuspensionState(processInstanceKey)).isEqualTo(State.RESUMING);
  }

  @Test
  public void shouldReportIsSuspendedBeforeAndAfterSettingMarker() {
    // given
    final long processInstanceKey = 1L;

    // when - then
    assertThat(suspensionState.isSuspended(processInstanceKey)).isFalse();

    // when
    suspensionState.setSuspensionState(processInstanceKey, State.SUSPENDED);

    // then
    assertThat(suspensionState.isSuspended(processInstanceKey)).isTrue();
  }

  @Test
  public void shouldRemoveSuspensionState() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.setSuspensionState(processInstanceKey, State.SUSPENDED);

    // when
    suspensionState.removeSuspensionState(processInstanceKey);

    // then
    assertThat(suspensionState.isSuspended(processInstanceKey)).isFalse();
    assertThat(suspensionState.getSuspensionState(processInstanceKey)).isNull();
  }

  @Test
  public void shouldNotFailWhenRemovingAbsentSuspensionState() {
    // given
    final long processInstanceKey = 1L;

    // when - then (no exception)
    assertThatCode(() -> suspensionState.removeSuspensionState(processInstanceKey))
        .doesNotThrowAnyException();
  }

  @Test
  public void shouldKeepSuspensionMarkerAndBufferedCommandsIndependent() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.setSuspensionState(processInstanceKey, State.SUSPENDED);
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));

    // when - clearing the buffered commands
    suspensionState.clearBufferedCommands(processInstanceKey);

    // then - the suspension marker is untouched
    assertThat(suspensionState.getSuspensionState(processInstanceKey)).isEqualTo(State.SUSPENDED);

    // given - a fresh buffered command
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when - removing the suspension marker
    suspensionState.removeSuspensionState(processInstanceKey);

    // then - the buffered command is untouched
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(processInstanceKey, (key, value) -> visitedKeys.add(key));
    assertThat(visitedKeys).containsExactly(20L);
  }

  @Test
  public void shouldBufferAndVisitSingleCommand() {
    // given
    final long processInstanceKey = 1L;
    final long commandRecordKey = 2L;
    final long bufferedCommandKey = 10L;
    final var command = bufferedCommandRecord(processInstanceKey, commandRecordKey);

    // when
    suspensionState.bufferCommand(bufferedCommandKey, command);

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    final List<BufferedCommandRecordValue> visitedValues = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey,
        (key, value) -> {
          visitedKeys.add(key);
          visitedValues.add(value);
        });

    assertThat(visitedKeys).containsExactly(bufferedCommandKey);
    assertThat(visitedValues).hasSize(1);
    final var visited = visitedValues.get(0);
    assertThat(visited.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(visited.getCommandKey()).isEqualTo(commandRecordKey);
    assertThat(visited.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
    assertThat(visited.getIntent()).isEqualTo(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    assertThat(visited.getCommandValue()).isInstanceOf(ProcessInstanceRecord.class);
    final var payload = (ProcessInstanceRecord) visited.getCommandValue();
    assertThat(payload.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(payload.getBpmnProcessId()).isEqualTo("process");
  }

  @Test
  public void shouldVisitBufferedCommandsInAscendingKeyOrderRegardlessOfInsertionOrder() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKey, 3L));
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(processInstanceKey, (key, value) -> visitedKeys.add(key));

    // then
    assertThat(visitedKeys).containsExactly(10L, 20L, 30L);
  }

  @Test
  public void shouldNotAliasVisitedCommandsAcrossMultipleRows() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKey, 3L));

    // when
    final List<BufferedCommandRecordValue> visitedValues = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, value) -> visitedValues.add(value));

    // then
    assertThat(visitedValues).hasSize(3);
    assertThat(visitedValues.get(0).getCommandKey()).isEqualTo(1L);
    assertThat(visitedValues.get(1).getCommandKey()).isEqualTo(2L);
    assertThat(visitedValues.get(2).getCommandKey()).isEqualTo(3L);
  }

  @Test
  public void shouldOnlyVisitBufferedCommandsOfRequestedProcessInstance() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyB, 2L));

    // when
    final List<Long> visitedKeysForA = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKeyA, (key, value) -> visitedKeysForA.add(key));

    final List<Long> visitedKeysForB = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKeyB, (key, value) -> visitedKeysForB.add(key));

    // then
    assertThat(visitedKeysForA).containsExactly(10L);
    assertThat(visitedKeysForB).containsExactly(20L);
  }

  @Test
  public void shouldFindNextBufferedCommandRegardlessOfInsertionOrder() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKey, 3L));
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, -1L);

    // then
    assertThat(lookup.command()).isPresent();
    assertThat(lookup.command().get().key()).isEqualTo(10L);
    assertThat(lookup.command().get().command().getCommandKey()).isEqualTo(1L);
    // 20L and 30L remain buffered after the returned 10L
    assertThat(lookup.hasMore()).isTrue();
  }

  @Test
  public void shouldSkipEntryMatchingAfterCommandKeyAndReturnTheNextOne() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKey, 3L));

    // when - afterCommandKey (10L) is still buffered, unlike the drain hot path where it was
    // already removed by the DRAINED applier before this call is made
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, 10L);

    // then
    assertThat(lookup.command()).isPresent();
    assertThat(lookup.command().get().key()).isEqualTo(20L);
    // 30L remains buffered after the returned 20L
    assertThat(lookup.hasMore()).isTrue();
  }

  @Test
  public void shouldFindNextBufferedCommandOfRequestedProcessInstanceOnly() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyB, 2L));

    // when
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKeyB, -1L);

    // then
    assertThat(lookup.command().orElseThrow().key()).isEqualTo(20L);
    // B has only one command buffered - A's remaining command must not leak into B's hasMore
    assertThat(lookup.hasMore()).isFalse();
  }

  @Test
  public void shouldNotFindNextBufferedCommandWithNothingBuffered() {
    // given
    final long processInstanceKey = 1L;

    // when
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, -1L);

    // then
    assertThat(lookup.command()).isEmpty();
    assertThat(lookup.hasMore()).isFalse();
  }

  @Test
  public void shouldFindNextBufferedCommandAfterTheOldestIsRemoved() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when - the drain removes the head of the buffer
    suspensionState.removeBufferedCommand(processInstanceKey, 10L);
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, -1L);

    // then - 20L is the only one left, so nothing follows it
    assertThat(lookup.command().orElseThrow().key()).isEqualTo(20L);
    assertThat(lookup.hasMore()).isFalse();
  }

  @Test
  public void shouldReportHasMoreFalseWhenOnlyOneCommandBuffered() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));

    // when
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, -1L);

    // then
    assertThat(lookup.command().orElseThrow().key()).isEqualTo(10L);
    assertThat(lookup.hasMore()).isFalse();
  }

  @Test
  public void shouldReportHasMoreTrueWithExactlyTwoBuffered() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, -1L);

    // then
    assertThat(lookup.command().orElseThrow().key()).isEqualTo(10L);
    assertThat(lookup.hasMore()).isTrue();
  }

  @Test
  public void shouldReportEmptyAndHasMoreFalseWhenQueryingAfterTheOnlyRemainingCommand() {
    // given - a single buffered command, not yet removed
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));

    // when - asking for whatever comes after the only entry there is
    final var lookup = suspensionState.findNextBufferedCommand(processInstanceKey, 10L);

    // then
    assertThat(lookup.command()).isEmpty();
    assertThat(lookup.hasMore()).isFalse();
  }

  @Test
  public void shouldNotLeakHasMoreAcrossProcessInstances() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyA, 2L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKeyB, 3L));

    // when
    final var lookupForA = suspensionState.findNextBufferedCommand(processInstanceKeyA, -1L);
    final var lookupForB = suspensionState.findNextBufferedCommand(processInstanceKeyB, -1L);

    // then - A has a second command buffered (20L), B does not (only 30L)
    assertThat(lookupForA.command().orElseThrow().key()).isEqualTo(10L);
    assertThat(lookupForA.hasMore()).isTrue();
    assertThat(lookupForB.command().orElseThrow().key()).isEqualTo(30L);
    assertThat(lookupForB.hasMore()).isFalse();
  }

  @Test
  public void shouldRemoveExactlyOneBufferedCommand() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    suspensionState.removeBufferedCommand(processInstanceKey, 10L);

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(processInstanceKey, (key, value) -> visitedKeys.add(key));
    assertThat(visitedKeys).containsExactly(20L);
  }

  @Test
  public void shouldClearAllBufferedCommandsForProcessInstanceOnly() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKeyB, 2L));

    // when
    suspensionState.clearBufferedCommands(processInstanceKeyA);

    // then
    final List<Long> visitedKeysForA = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKeyA, (key, value) -> visitedKeysForA.add(key));
    assertThat(visitedKeysForA).isEmpty();

    final List<Long> visitedKeysForB = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKeyB, (key, value) -> visitedKeysForB.add(key));
    assertThat(visitedKeysForB).containsExactly(30L);
  }

  @Test
  public void shouldCountBufferedCommandsForProcessInstanceOnly() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    assertThat(suspensionState.countBufferedCommands(processInstanceKeyA)).isZero();

    // when
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyA, 2L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKeyB, 3L));

    // then
    assertThat(suspensionState.countBufferedCommands(processInstanceKeyA)).isEqualTo(2);
    assertThat(suspensionState.countBufferedCommands(processInstanceKeyB)).isEqualTo(1);

    // when — removing one of A's commands
    suspensionState.removeBufferedCommand(processInstanceKeyA, 10L);

    // then
    assertThat(suspensionState.countBufferedCommands(processInstanceKeyA)).isEqualTo(1);
  }

  @Test
  public void shouldNotFailWhenRemovingOrClearingBufferedCommandsWithNothingBuffered() {
    // given
    final long processInstanceKey = 1L;

    // when - then (no exception)
    assertThatCode(() -> suspensionState.removeBufferedCommand(processInstanceKey, 10L))
        .doesNotThrowAnyException();
    assertThatCode(() -> suspensionState.clearBufferedCommands(processInstanceKey))
        .doesNotThrowAnyException();

    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(processInstanceKey, (key, value) -> visitedKeys.add(key));
    assertThat(visitedKeys).isEmpty();
  }

  private BufferedCommandRecord bufferedCommandRecord(
      final long processInstanceKey, final long commandKey) {
    return new BufferedCommandRecord()
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionKey(1)
        .setTenantId("tenant")
        .setCommandKey(commandKey)
        .setValueType(ValueType.PROCESS_INSTANCE)
        .setIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
        .setCommandValue(
            new ProcessInstanceRecord()
                .setProcessInstanceKey(processInstanceKey)
                .setBpmnProcessId("process"));
  }
}
