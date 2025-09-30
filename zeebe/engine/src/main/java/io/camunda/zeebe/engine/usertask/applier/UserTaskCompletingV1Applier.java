/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.usertask.applier;

import io.camunda.zeebe.engine.common.state.TypedEventApplier;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.common.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.usertask.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.HandlesIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

@HandlesIntent(intent = UserTaskIntent.class, type = "COMPLETING")
public final class UserTaskCompletingV1Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;

  public UserTaskCompletingV1Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.COMPLETING);
  }
}
