/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class SuspensionAppliersTest {

  private MutableProcessingState processingState;

  private MutableSuspensionState suspensionState;
  private ProcessInstanceSuspendedApplier suspendedApplier;
  private ProcessInstanceResumingApplier resumingApplier;
  private ProcessInstanceResumedApplier resumedApplier;
  private BufferedCommandBufferedApplier bufferedApplier;
  private BufferedCommandDrainedApplier drainedApplier;

  @BeforeEach
  public void setup() {
    suspensionState = processingState.getSuspensionState();
    suspendedApplier = new ProcessInstanceSuspendedApplier(suspensionState);
    resumingApplier = new ProcessInstanceResumingApplier(suspensionState);
    resumedApplier = new ProcessInstanceResumedApplier(suspensionState);
    bufferedApplier = new BufferedCommandBufferedApplier(suspensionState);
    drainedApplier = new BufferedCommandDrainedApplier(suspensionState);
  }

  @Test
  void shouldMarkProcessInstanceAsSuspended() {
    // given
    final long processInstanceKey = 1L;
    final var record = new ProcessInstanceRecord();

    // when
    suspendedApplier.applyState(processInstanceKey, record);

    // then
    assertThat(suspensionState.isSuspended(processInstanceKey)).isTrue();
    assertThat(suspensionState.getSuspensionState(processInstanceKey))
        .isEqualTo(SuspensionState.State.SUSPENDED);
  }

  @Test
  void shouldSwitchMarkerToResumingWithoutTouchingTheBuffer() {
    // given
    final long processInstanceKey = 5L;
    final long bufferedCommandKey = 50L;
    suspensionState.setSuspensionState(processInstanceKey, SuspensionState.State.SUSPENDED);
    bufferedApplier.applyState(
        bufferedCommandKey,
        new BufferedCommandRecord().setProcessInstanceKey(processInstanceKey).setCommandKey(51L));

    // when
    resumingApplier.applyState(processInstanceKey, new ProcessInstanceRecord());

    // then - the marker is still present, so the suspension gate keeps recognizing the instance
    assertThat(suspensionState.isSuspended(processInstanceKey)).isTrue();
    assertThat(suspensionState.getSuspensionState(processInstanceKey))
        .isEqualTo(SuspensionState.State.RESUMING);

    // and the buffered commands are left for the drain to replay
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, command) -> visitedKeys.add(key));
    assertThat(visitedKeys).containsExactly(bufferedCommandKey);
  }

  @Test
  void shouldClearSuspensionAndBufferedCommandsOnResume() {
    // given
    final long processInstanceKey = 2L;
    final long bufferedCommandKey = 20L;
    suspensionState.setSuspensionState(processInstanceKey, SuspensionState.State.SUSPENDED);
    final var bufferedRecord =
        new BufferedCommandRecord().setProcessInstanceKey(processInstanceKey).setCommandKey(21L);
    bufferedApplier.applyState(bufferedCommandKey, bufferedRecord);

    // when
    resumedApplier.applyState(processInstanceKey, new ProcessInstanceRecord());

    // then
    assertThat(suspensionState.isSuspended(processInstanceKey)).isFalse();
    assertThat(suspensionState.getSuspensionState(processInstanceKey)).isNull();

    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, command) -> visitedKeys.add(key));
    assertThat(visitedKeys).isEmpty();
  }

  @Test
  void shouldBufferCommand() {
    // given
    final long processInstanceKey = 3L;
    final long bufferedCommandKey = 30L;
    final long commandRecordKey = 31L;
    final var bufferedRecord =
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setCommandKey(commandRecordKey)
            .setValueType(ValueType.PROCESS_INSTANCE)
            .setIntent(BufferedCommandIntent.BUFFER);

    // when
    bufferedApplier.applyState(bufferedCommandKey, bufferedRecord);

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    final List<BufferedCommandRecordValue> visitedCommands = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey,
        (key, command) -> {
          visitedKeys.add(key);
          visitedCommands.add(command);
        });

    assertThat(visitedKeys).containsExactly(bufferedCommandKey);
    assertThat(visitedCommands)
        .singleElement()
        .extracting("commandKey")
        .isEqualTo(commandRecordKey);
  }

  @Test
  void shouldRemoveOnlyTheDrainedBufferedCommand() {
    // given
    final long processInstanceKey = 4L;
    final long firstBufferedCommandKey = 40L;
    final long secondBufferedCommandKey = 41L;
    final long firstCommandRecordKey = 400L;
    final long secondCommandRecordKey = 401L;

    bufferedApplier.applyState(
        firstBufferedCommandKey,
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setCommandKey(firstCommandRecordKey));
    bufferedApplier.applyState(
        secondBufferedCommandKey,
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setCommandKey(secondCommandRecordKey));

    // when
    drainedApplier.applyState(
        firstBufferedCommandKey,
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setCommandKey(firstCommandRecordKey));

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, command) -> visitedKeys.add(key));

    assertThat(visitedKeys).containsExactly(secondBufferedCommandKey);
  }
}
