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
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public final class UserTaskCreatingApplier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableUserTaskState userTaskState;

  public UserTaskCreatingApplier(final MutableProcessingState processingState) {
    elementInstanceState = processingState.getElementInstanceState();
    userTaskState = processingState.getUserTaskState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    // todo: should we actually store the user task fully already, or wait until we finalize?
    //    final var assignee = value.getAssignee();
    //    value.unsetAssignee();
    userTaskState.create(value);

    //    value.setAssignee(assignee);
    //    value.setAssigneeChanged();
    userTaskState.storeIntermediateState(value, LifecycleState.CREATING);

    final long elementInstanceKey = value.getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        elementInstance.setUserTaskKey(key);
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }
}
