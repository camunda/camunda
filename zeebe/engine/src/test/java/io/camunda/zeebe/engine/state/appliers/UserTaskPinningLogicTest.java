/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskPinningLogicTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private Map<UserTaskIntent, TypedEventApplier<UserTaskIntent, UserTaskRecord>> appliers;

  private MutableGlobalListenersState globalListenersState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  private long key = 1L;

  @BeforeEach
  public void setup() {
    globalListenersState = processingState.getGlobalListenersState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @ParameterizedTest
  @MethodSource("eventAppliers")
  public void shouldStoreCopyOfPinnedConfigurationWhileLifecycleEventIsActive(
      final AppliersInfo appliersInfo) {
    final var startApplier = appliersInfo.startApplier().apply(processingState);

    // given a global listeners configuration stored in state and a user task
    final var expectedConfig = newGlobalListeners();
    final var expectedConfigKey = expectedConfig.getGlobalListenerBatchKey();
    updateCurrentConfiguration(expectedConfig);

    final var userTaskRecord = newUserTaskRecord();
    final var userTaskKey = userTaskRecord.getUserTaskKey();
    // If the intent is not CREATE, we need to create the user task first,
    // otherwise the creation is handled by the applier itself
    if (appliersInfo.intent() != UserTaskIntent.CREATE) {
      testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
      testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
    }

    // when a start event if applied for the user task
    startApplier.applyState(userTaskKey, userTaskRecord);

    // then the pinned configuration can be retrieved from state
    final var pinnedConfig = globalListenersState.getVersionedConfig(expectedConfigKey);
    assertThat(pinnedConfig).as("Stored configuration copy").isNotNull().isEqualTo(expectedConfig);
    assertThat(globalListenersState.isConfigurationVersionPinned(expectedConfigKey))
        .as("Configuration is pinned")
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("eventAppliers")
  public void shouldNotChangePinnedConfigurationIfGlobalConfigurationChanges(
      final AppliersInfo appliersInfo) {
    final var startApplier = appliersInfo.startApplier().apply(processingState);

    // given a global listeners configuration stored in state and pinned by a user task
    final var firstConfig = newGlobalListeners();
    final var firstConfigKey = firstConfig.getGlobalListenerBatchKey();
    updateCurrentConfiguration(firstConfig);

    final var userTaskRecord = newUserTaskRecord();
    final var userTaskKey = userTaskRecord.getUserTaskKey();
    // If the intent is not CREATE, we need to create the user task first,
    // otherwise the creation is handled by the applier itself
    if (appliersInfo.intent() != UserTaskIntent.CREATE) {
      testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
      testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
    }
    // start the lifecycle event, which pins the configuration
    startApplier.applyState(userTaskKey, userTaskRecord);

    // when the global configuration is updated
    final GlobalListenerBatchRecord newConfig = newGlobalListeners();
    updateCurrentConfiguration(newConfig);

    // then the old configuration should still be pinned
    final var pinnedConfig = globalListenersState.getVersionedConfig(firstConfigKey);
    assertThat(pinnedConfig).as("Stored configuration copy").isNotNull().isEqualTo(firstConfig);
    assertThat(globalListenersState.isConfigurationVersionPinned(firstConfigKey))
        .as("Configuration is pinned")
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("eventAppliers")
  public void shouldKeepACopyOfTheConfigurationIfItIsUnpinnedFromOneTaskButReferencedByAnother(
      final AppliersInfo appliersInfo) {
    final var startApplier = appliersInfo.startApplier().apply(processingState);
    final var endApplier = appliersInfo.endApplier().apply(processingState);

    // given a global listeners configuration stored in state and pinned by two user tasks
    final var expectedConfig = newGlobalListeners();
    final var firstConfigKey = expectedConfig.getGlobalListenerBatchKey();
    updateCurrentConfiguration(expectedConfig);

    final var userTask1 = newUserTaskRecord();
    final var userTaskKey1 = userTask1.getUserTaskKey();
    final var userTask2 = newUserTaskRecord();
    final var userTaskKey2 = userTask2.getUserTaskKey();
    // If the intent is not CREATE, we need to create the user tasks first,
    // otherwise the creation is handled by the applier itself
    if (appliersInfo.intent() != UserTaskIntent.CREATE) {
      testSetup.applyEventToState(userTaskKey1, UserTaskIntent.CREATING, userTask1);
      testSetup.applyEventToState(userTaskKey2, UserTaskIntent.CREATING, userTask2);
      testSetup.applyEventToState(userTaskKey1, UserTaskIntent.CREATED, userTask1);
      testSetup.applyEventToState(userTaskKey2, UserTaskIntent.CREATED, userTask2);
    }
    // start the lifecycle event, which pins the configuration
    startApplier.applyState(userTaskKey1, userTask1);
    startApplier.applyState(userTaskKey2, userTask2);

    // when one user task unpins the configuration by ending its lifecycle event
    endApplier.applyState(userTaskKey1, userTask1);

    // then the old configuration should still be pinned
    final var pinnedConfig = globalListenersState.getVersionedConfig(firstConfigKey);
    assertThat(pinnedConfig).as("Stored configuration copy").isNotNull().isEqualTo(expectedConfig);
    assertThat(globalListenersState.isConfigurationVersionPinned(firstConfigKey))
        .as("Configuration is pinned")
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("eventAppliers")
  public void shouldRemoveCopyOfTheConfigurationIfItIsUnpinnedFromAllReferencingTasks(
      final AppliersInfo appliersInfo) {
    final var startApplier = appliersInfo.startApplier().apply(processingState);
    final var endApplier = appliersInfo.endApplier().apply(processingState);

    // given a global listeners configuration stored in state and pinned by two user tasks
    final var expectedConfig = newGlobalListeners();
    final var expectedConfigKey = expectedConfig.getGlobalListenerBatchKey();
    updateCurrentConfiguration(expectedConfig);

    final var userTask1 = newUserTaskRecord();
    final var userTaskKey1 = userTask1.getUserTaskKey();
    final var userTask2 = newUserTaskRecord();
    final var userTaskKey2 = userTask2.getUserTaskKey();
    // If the intent is not CREATE, we need to create the user tasks first,
    // otherwise the creation is handled by the applier itself
    if (appliersInfo.intent() != UserTaskIntent.CREATE) {
      testSetup.applyEventToState(userTaskKey1, UserTaskIntent.CREATING, userTask1);
      testSetup.applyEventToState(userTaskKey1, UserTaskIntent.CREATED, userTask1);
      testSetup.applyEventToState(userTaskKey2, UserTaskIntent.CREATING, userTask2);
      testSetup.applyEventToState(userTaskKey2, UserTaskIntent.CREATED, userTask2);
    }
    // start the lifecycle event, which pins the configuration
    startApplier.applyState(userTaskKey1, userTask1);
    startApplier.applyState(userTaskKey2, userTask2);

    // when one both task unpin the configuration by ending their lifecycle events
    endApplier.applyState(userTaskKey1, userTask1);
    endApplier.applyState(userTaskKey2, userTask1);

    // then the old configuration should not be pinned any more
    final var pinnedConfig = globalListenersState.getVersionedConfig(expectedConfigKey);
    assertThat(pinnedConfig).as("Stored configuration copy").isNull();
    assertThat(globalListenersState.isConfigurationVersionPinned(expectedConfigKey))
        .as("Configuration is pinned")
        .isFalse();
  }

  private void updateCurrentConfiguration(final GlobalListenerBatchRecord newConfig) {
    final var oldConfig = globalListenersState.getCurrentConfig();
    if (oldConfig != null) {
      oldConfig
          .getTaskListeners()
          .forEach(listener -> globalListenersState.delete((GlobalListenerRecord) listener));
    }
    newConfig
        .getTaskListeners()
        .forEach(listener -> globalListenersState.create((GlobalListenerRecord) listener));
    globalListenersState.updateConfigKey(newConfig.getGlobalListenerBatchKey());
  }

  private UserTaskRecord newUserTaskRecord() {
    return new UserTaskRecord().setUserTaskKey(newKey()).setElementInstanceKey(newKey());
  }

  private GlobalListenerBatchRecord newGlobalListeners() {
    final GlobalListenerBatchRecord record =
        new GlobalListenerBatchRecord().setGlobalListenerBatchKey(newKey());
    final int numberOfListeners = new Random().nextInt(2, 10);
    for (int i = 0; i < numberOfListeners; i++) {
      record.addTaskListener(
          new GlobalListenerRecord()
              .setId("GlobalListener_" + i)
              .setType("global" + i)
              .setEventTypes(List.of("creating", "assigning"))
              .setRetries(i)
              .setAfterNonGlobal(i % 2 == 0));
    }
    return record;
  }

  private long newKey() {
    return key++;
  }

  private static Stream<Arguments> eventAppliers() {
    return Stream.of(
        Arguments.of(
            new AppliersInfo(
                UserTaskIntent.ASSIGN,
                UserTaskAssigningV3Applier::new,
                UserTaskAssignedV4Applier::new)),
        Arguments.of(
            new AppliersInfo(
                UserTaskIntent.CANCEL,
                UserTaskCancelingV3Applier::new,
                UserTaskCanceledV2Applier::new)),
        Arguments.of(
            new AppliersInfo(
                UserTaskIntent.COMPLETE,
                UserTaskCompletingV3Applier::new,
                UserTaskCompletedV3Applier::new)),
        Arguments.of(
            new AppliersInfo(
                UserTaskIntent.CREATE,
                UserTaskCreatingV3Applier::new,
                UserTaskCreatedV3Applier::new)),
        Arguments.of(
            new AppliersInfo(
                UserTaskIntent.UPDATE,
                UserTaskUpdatingV3Applier::new,
                UserTaskUpdatedV3Applier::new)));
  }

  record AppliersInfo(
      UserTaskIntent intent,
      Function<MutableProcessingState, TypedEventApplier<UserTaskIntent, UserTaskRecord>>
          startApplier,
      Function<MutableProcessingState, TypedEventApplier<UserTaskIntent, UserTaskRecord>>
          endApplier) {}
}
