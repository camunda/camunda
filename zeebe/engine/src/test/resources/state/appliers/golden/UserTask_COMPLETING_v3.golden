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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public final class UserTaskCompletingV3Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableGlobalListenersState globalListenersState;

  public UserTaskCompletingV3Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.COMPLETING);
    pinGlobalListenersConfig(value);
    userTaskState.storeIntermediateState(value, LifecycleState.COMPLETING);
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
