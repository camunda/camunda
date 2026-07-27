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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
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
  private ProcessInstanceResumedApplier resumedApplier;
  private ProcessInstanceBufferedCommandBufferedApplier bufferedApplier;
  private ProcessInstanceBufferedCommandDrainedApplier drainedApplier;

  @BeforeEach
  public void setup() {
    suspensionState = processingState.getSuspensionState();
    suspendedApplier = new ProcessInstanceSuspendedApplier(suspensionState);
    resumedApplier = new ProcessInstanceResumedApplier(suspensionState);
    bufferedApplier = new ProcessInstanceBufferedCommandBufferedApplier(suspensionState);
    drainedApplier = new ProcessInstanceBufferedCommandDrainedApplier(suspensionState);
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
  void shouldClearSuspensionAndBufferedCommandsOnResume() {
    // given
    final long processInstanceKey = 2L;
    final long bufferedCommandKey = 20L;
    suspensionState.setSuspensionState(processInstanceKey, SuspensionState.State.SUSPENDED);
    final var bufferedRecord =
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(21L);
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
    final long elementInstanceKey = 31L;
    final var bufferedRecord =
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setValueType(ValueType.PROCESS_INSTANCE)
            .setIntent(ProcessInstanceBufferedCommandIntent.BUFFER);

    // when
    bufferedApplier.applyState(bufferedCommandKey, bufferedRecord);

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    final List<ProcessInstanceBufferedCommandRecordValue> visitedCommands = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey,
        (key, command) -> {
          visitedKeys.add(key);
          visitedCommands.add(command);
        });

    assertThat(visitedKeys).containsExactly(bufferedCommandKey);
    assertThat(visitedCommands)
        .singleElement()
        .extracting("elementInstanceKey")
        .isEqualTo(elementInstanceKey);
  }

  @Test
  void shouldRemoveOnlyTheDrainedBufferedCommand() {
    // given
    final long processInstanceKey = 4L;
    final long firstBufferedCommandKey = 40L;
    final long secondBufferedCommandKey = 41L;
    final long firstElementInstanceKey = 400L;
    final long secondElementInstanceKey = 401L;

    bufferedApplier.applyState(
        firstBufferedCommandKey,
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(firstElementInstanceKey));
    bufferedApplier.applyState(
        secondBufferedCommandKey,
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(secondElementInstanceKey));

    // when
    drainedApplier.applyState(
        firstBufferedCommandKey,
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(firstElementInstanceKey));

    // then
    final List<Long> visitedKeys = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, command) -> visitedKeys.add(key));

    assertThat(visitedKeys).containsExactly(secondBufferedCommandKey);
  }
}
