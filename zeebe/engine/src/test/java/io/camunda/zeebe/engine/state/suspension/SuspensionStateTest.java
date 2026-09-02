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
  public void shouldGetOldestBufferedCommandRegardlessOfInsertionOrder() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(30L, bufferedCommandRecord(processInstanceKey, 3L));
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    final var oldest = suspensionState.getOldestBufferedCommand(processInstanceKey);

    // then
    assertThat(oldest).isPresent();
    assertThat(oldest.get().key()).isEqualTo(10L);
    assertThat(oldest.get().command().getCommandKey()).isEqualTo(1L);
  }

  @Test
  public void shouldGetOldestBufferedCommandOfRequestedProcessInstanceOnly() {
    // given
    final long processInstanceKeyA = 1L;
    final long processInstanceKeyB = 2L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKeyA, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKeyB, 2L));

    // when - then
    assertThat(suspensionState.getOldestBufferedCommand(processInstanceKeyB).orElseThrow().key())
        .isEqualTo(20L);
  }

  @Test
  public void shouldNotGetOldestBufferedCommandWithNothingBuffered() {
    // given
    final long processInstanceKey = 1L;

    // when - then
    assertThat(suspensionState.getOldestBufferedCommand(processInstanceKey)).isEmpty();
  }

  @Test
  public void shouldGetNextOldestBufferedCommandAfterTheOldestIsRemoved() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when - the drain removes the head of the buffer
    suspensionState.removeBufferedCommand(10L);

    // then
    assertThat(suspensionState.getOldestBufferedCommand(processInstanceKey).orElseThrow().key())
        .isEqualTo(20L);
  }

  @Test
  public void shouldRemoveExactlyOneBufferedCommand() {
    // given
    final long processInstanceKey = 1L;
    suspensionState.bufferCommand(10L, bufferedCommandRecord(processInstanceKey, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(processInstanceKey, 2L));

    // when
    suspensionState.removeBufferedCommand(10L);

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
  public void shouldNotFailWhenRemovingOrClearingBufferedCommandsWithNothingBuffered() {
    // given
    final long processInstanceKey = 1L;

    // when - then (no exception)
    assertThatCode(() -> suspensionState.removeBufferedCommand(10L)).doesNotThrowAnyException();
    assertThatCode(() -> suspensionState.clearBufferedCommands(processInstanceKey))
        .doesNotThrowAnyException();

    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(processInstanceKey, (key, value) -> visitedKeys.add(key));
    assertThat(visitedKeys).isEmpty();
  }

  @Test
  public void shouldCountSuspensionMarkersAcrossInsertAndRemove() {
    // given
    assertThat(suspensionState.countSuspensionMarkers()).isZero();

    // when
    suspensionState.setSuspensionState(1L, State.SUSPENDED);
    suspensionState.setSuspensionState(2L, State.RESUMING);

    // then
    assertThat(suspensionState.countSuspensionMarkers()).isEqualTo(2);

    // when — overwriting a state does not create a second row
    suspensionState.setSuspensionState(1L, State.RESUMING);

    // then
    assertThat(suspensionState.countSuspensionMarkers()).isEqualTo(2);

    // when — removing one marker
    suspensionState.removeSuspensionState(1L);

    // then
    assertThat(suspensionState.countSuspensionMarkers()).isEqualTo(1);
  }

  @Test
  public void shouldCountBufferedCommandsAcrossInsertRemoveAndClear() {
    // given
    assertThat(suspensionState.countBufferedCommands()).isZero();

    // when
    suspensionState.bufferCommand(10L, bufferedCommandRecord(1L, 1L));
    suspensionState.bufferCommand(20L, bufferedCommandRecord(1L, 2L));
    suspensionState.bufferCommand(30L, bufferedCommandRecord(2L, 3L));

    // then
    assertThat(suspensionState.countBufferedCommands()).isEqualTo(3);

    // when — removing one command
    suspensionState.removeBufferedCommand(10L);

    // then
    assertThat(suspensionState.countBufferedCommands()).isEqualTo(2);

    // when — clearing all commands for PI 1 (one remaining)
    suspensionState.clearBufferedCommands(1L);

    // then — only PI 2's command remains
    assertThat(suspensionState.countBufferedCommands()).isEqualTo(1);
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
