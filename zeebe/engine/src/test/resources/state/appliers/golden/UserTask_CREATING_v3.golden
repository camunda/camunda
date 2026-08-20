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
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class UserTaskCreatingV3Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableUserTaskState userTaskState;
  private final MutableGlobalListenersState globalListenersState;

  public UserTaskCreatingV3Applier(final MutableProcessingState processingState) {
    elementInstanceState = processingState.getElementInstanceState();
    userTaskState = processingState.getUserTaskState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    final var valueWithoutAssignee = value.copy().unsetAssignee();
    userTaskState.create(valueWithoutAssignee);
    pinGlobalListenersConfig(value);
    userTaskState.storeIntermediateState(value, LifecycleState.CREATING);
    userTaskState.storeInitialAssignee(key, value.getAssignee());

    final long elementInstanceKey = value.getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        elementInstance.setUserTaskKey(key);
        elementInstanceState.updateInstance(elementInstance);
      }
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
