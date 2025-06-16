/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState.AsyncRequest;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableAsyncRequestState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Set;

public final class UserTaskCancelingV2Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableVariableState variableState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableAsyncRequestState asyncRequestState;

  public UserTaskCancelingV2Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    elementInstanceState = processingState.getElementInstanceState();
    asyncRequestState = processingState.getAsyncRequestState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.CANCELING);

    // Clean up data that may have been persisted by a previous transition
    variableState.removeVariableDocumentState(value.getElementInstanceKey());
    userTaskState.deleteIntermediateStateIfExists(key);
    deleteAsyncRequestsIfExists(value);
    resetTaskListenerIndices(value);

    // Persist new data related to "canceling" user task transition
    userTaskState.storeIntermediateState(value, LifecycleState.CANCELING);
    userTaskState.deleteInitialAssignee(key);
  }

  private void deleteAsyncRequestsIfExists(final UserTaskRecord value) {
    final var expectedValueTypes = Set.of(ValueType.USER_TASK, ValueType.VARIABLE_DOCUMENT);
    asyncRequestState
        .findAllRequestsByScopeKey(value.getElementInstanceKey())
        .filter(metadata -> expectedValueTypes.contains(metadata.valueType()))
        .map(AsyncRequest::record)
        .forEach(asyncRequestState::deleteRequest);
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
