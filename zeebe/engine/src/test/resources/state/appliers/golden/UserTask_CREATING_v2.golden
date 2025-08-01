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

public class UserTaskCreatingV2Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableUserTaskState userTaskState;

  public UserTaskCreatingV2Applier(final MutableProcessingState processingState) {
    elementInstanceState = processingState.getElementInstanceState();
    userTaskState = processingState.getUserTaskState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    final var valueWithoutAssignee = value.copy().unsetAssignee();
    userTaskState.create(valueWithoutAssignee);
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
}
