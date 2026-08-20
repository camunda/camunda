/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public final class UserTaskCancelingV3Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableVariableState variableState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableGlobalListenersState globalListenersState;

  public UserTaskCancelingV3Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    elementInstanceState = processingState.getElementInstanceState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.CANCELING);

    // Clean up data that may have been persisted by a previous transition
    variableState.removeVariableDocumentState(value.getElementInstanceKey());
    userTaskState.deleteIntermediateStateIfExists(key);
    resetTaskListenerIndices(value);

    pinGlobalListenersConfig(value);

    // Persist new data related to "canceling" user task transition
    userTaskState.storeIntermediateState(value, LifecycleState.CANCELING);
    userTaskState.deleteInitialAssignee(key);
  }

  private void resetTaskListenerIndices(final UserTaskRecord record) {
    final long userTaskInstanceKey = record.getElementInstanceKey();
    final var userTaskInstance = elementInstanceState.getInstance(userTaskInstanceKey);

    if (userTaskInstance != null) {
      userTaskInstance.resetTaskListenerIndices();
      elementInstanceState.updateInstance(userTaskInstance);
    }
  }

  public void pinGlobalListenersConfig(final UserTaskRecord userTaskRecord) {
    // Only pin the configuration if it exists
    final var currentConfig = globalListenersState.getCurrentConfig();
    if (currentConfig == null) {
      return;
    }

    final long currentConfigKey = currentConfig.getGlobalListenerBatchKey();

    // Create versioned config if it does not exist
    if (!globalListenersState.isConfigurationVersionStored(currentConfigKey)) {
      globalListenersState.storeConfigurationVersion(currentConfig);
    }

    // Create pinned entry
    globalListenersState.pinConfiguration(currentConfigKey, userTaskRecord.getUserTaskKey());

    // Update user task record to reference the pinned config
    // Note: this value is then stored in the intermediate state
    userTaskRecord.setListenersConfigKey(currentConfigKey);
  }
}
