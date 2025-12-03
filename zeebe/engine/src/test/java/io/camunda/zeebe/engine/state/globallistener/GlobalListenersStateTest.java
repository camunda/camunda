/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.List;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GlobalListenersStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableGlobalListenersState globalListenersState;

  private long key = 1L;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Test
  public void shouldCreateFirstConfiguration() {
    // given a global listeners record
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();

    // when the configuration is stored in state
    globalListenersState.updateCurrentConfiguration(expectedConfig);

    // then the configuration can be retrieved from state
    final var storedConfig = globalListenersState.getCurrentConfig().orElse(null);
    assertThat(storedConfig).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldUpdateConfiguration() {
    // given a global listeners configuration stored in state
    final GlobalListenerBatchRecord firstConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(firstConfig);

    // when the configuration is updated
    final GlobalListenerBatchRecord newConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(newConfig);

    // then the new configuration can be retrieved from state
    final var storedConfig = globalListenersState.getCurrentConfig().orElse(null);
    assertThat(storedConfig).isNotNull().isEqualTo(newConfig);
  }

  @Test
  public void shouldStoreCopyOfPinnedConfiguration() {
    // given a global listeners configuration stored in state
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(expectedConfig);

    // when a user task pins the current configuration
    final UserTaskRecord userTaskRecord = newUserTask();
    globalListenersState.pinCurrentConfiguration(userTaskRecord);

    // then the pinned configuration can be retrieved from state
    final var pinnedConfig =
        globalListenersState
            .getVersionedConfig(userTaskRecord.getListenersConfigKey())
            .orElseThrow();
    assertThat(pinnedConfig).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldNotChangePinnedConfigurationIfGlobalConfigurationChanges() {
    // given a global listeners configuration stored in state and pinned by a user task
    final GlobalListenerBatchRecord firstConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(firstConfig);

    final UserTaskRecord userTaskRecord = newUserTask();
    globalListenersState.pinCurrentConfiguration(userTaskRecord);

    // when the global configuration is updated
    final GlobalListenerBatchRecord newConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(newConfig);

    // then the old configuration should still be pinned for the user task
    final var pinnedConfig =
        globalListenersState
            .getVersionedConfig(userTaskRecord.getListenersConfigKey())
            .orElseThrow();
    assertThat(pinnedConfig).isNotNull().isEqualTo(firstConfig);
  }

  @Test
  public void shouldKeepACopyOfTheConfigurationIfItIsUnpinnedFromOneTaskButReferencedByAnother() {
    // given a global listeners configuration stored in state and pinned by two user tasks
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(expectedConfig);
    final UserTaskRecord task1 = newUserTask();
    globalListenersState.pinCurrentConfiguration(task1);
    final UserTaskRecord task2 = newUserTask();
    globalListenersState.pinCurrentConfiguration(task2);

    // when one user task unpins the configuration
    globalListenersState.unpinConfiguration(task1);

    // then
    final var pinnedConfig =
        globalListenersState.getVersionedConfig(task2.getListenersConfigKey()).orElseThrow();
    assertThat(pinnedConfig).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldRemoveCopyOfTheConfigurationIfItIsUnpinnedFromAllReferencingTasks() {
    // given a global listeners configuration stored in state and pinned by two user tasks
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    globalListenersState.updateCurrentConfiguration(expectedConfig);
    final UserTaskRecord task1 = newUserTask();
    globalListenersState.pinCurrentConfiguration(task1);
    final UserTaskRecord task2 = newUserTask();
    globalListenersState.pinCurrentConfiguration(task2);

    // when both user tasks unpin the configuration
    globalListenersState.unpinConfiguration(task1);
    globalListenersState.unpinConfiguration(task2);

    // then
    final var pinnedConfig = globalListenersState.getVersionedConfig(task2.getListenersConfigKey());
    Assertions.assertThat(pinnedConfig).isEmpty();
  }

  private GlobalListenerBatchRecord newGlobalListeners() {
    final GlobalListenerBatchRecord record =
        new GlobalListenerBatchRecord().setListenersConfigKey(newKey());
    final int numberOfListeners = new Random().nextInt(2, 10);
    for (int i = 0; i < numberOfListeners; i++) {
      record.addTaskListener(
          new GlobalListenerRecord()
              .setType("global" + i)
              .setEventTypes(List.of("creating", "assigning"))
              .setRetries(i)
              .setAfterNonGlobal(i % 2 == 0));
    }
    return record;
  }

  private UserTaskRecord newUserTask() {
    return new UserTaskRecord()
        .setElementInstanceKey(newKey())
        .setBpmnProcessId("process_" + newKey())
        .setElementId("task_" + newKey())
        .setProcessInstanceKey(newKey())
        .setProcessDefinitionKey(newKey())
        .setUserTaskKey(newKey());
  }

  private long newKey() {
    return key++;
  }
}
