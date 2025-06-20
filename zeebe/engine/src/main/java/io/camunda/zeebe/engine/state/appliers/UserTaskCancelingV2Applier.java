/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public final class UserTaskCancelingV2Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableVariableState variableState;
  private final MutableElementInstanceState elementInstanceState;

  public UserTaskCancelingV2Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.CANCELING);

    // Clean up data that may have been persisted by a previous transition
    variableState.removeVariableDocumentState(value.getElementInstanceKey());
    userTaskState.deleteIntermediateStateIfExists(key);
    resetTaskListenerIndices(value);

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
}
