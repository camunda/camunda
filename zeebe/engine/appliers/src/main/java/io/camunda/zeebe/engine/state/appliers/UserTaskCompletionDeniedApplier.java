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

public class UserTaskCompletionDeniedApplier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;

  private final MutableElementInstanceState elementInstanceState;

  public UserTaskCompletionDeniedApplier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.CREATED);
    userTaskState.deleteIntermediateState(key);

    final long elementInstanceKey = value.getElementInstanceKey();
    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    if (elementInstance != null) {
      final long scopeKey = elementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        elementInstance.resetTaskListenerIndices();
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }
}
