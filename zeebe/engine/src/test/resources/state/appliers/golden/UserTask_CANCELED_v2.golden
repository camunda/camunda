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
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public final class UserTaskCanceledV2Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableGlobalListenersState globalListenersState;

  public UserTaskCanceledV2Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    // Unpin global listeners configuration from the user task
    // Note that the pinning information is stored in the intermediate
    // state, which needs to be accessed before it is deleted
    final UserTaskRecord intermediateStateRecord =
        userTaskState.getIntermediateState(key).getRecord();
    if (intermediateStateRecord != null) {
      unpinGlobalListenersConfig(intermediateStateRecord);
    }

    userTaskState.deleteIntermediateStateIfExists(key);
    userTaskState.delete(key);
    resetCancelingTaskListenerIndex(value);
  }

  private void resetCancelingTaskListenerIndex(final UserTaskRecord record) {
    final long userTaskInstanceKey = record.getElementInstanceKey();
    final var userTaskInstance = elementInstanceState.getInstance(userTaskInstanceKey);

    if (userTaskInstance != null) {
      userTaskInstance.resetTaskListenerIndex(ZeebeTaskListenerEventType.canceling);
      elementInstanceState.updateInstance(userTaskInstance);
    }
  }

  public void unpinGlobalListenersConfig(final UserTaskRecord userTaskRecord) {
    final long pinnedConfigKey = userTaskRecord.getListenersConfigKey();
    // Only unpin if there is a pinned config
    if (pinnedConfigKey < 0) {
      return;
    }

    // Remove pinned entry
    globalListenersState.unpinConfiguration(pinnedConfigKey, userTaskRecord.getUserTaskKey());

    // If no other user task references this config, remove the versioned config
    if (!globalListenersState.isConfigurationVersionPinned(pinnedConfigKey)) {
      globalListenersState.deleteConfigurationVersion(pinnedConfigKey);
    }
  }
}
