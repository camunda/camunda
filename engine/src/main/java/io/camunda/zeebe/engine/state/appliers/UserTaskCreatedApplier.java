/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.usertask.MutableUserTaskState;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;

public class UserTaskCreatedApplier implements TypedEventApplier<UserTaskIntent, UserTaskRecordValue> {

  private final MutableUserTaskState state;

  public UserTaskCreatedApplier(final MutableUserTaskState userTaskState) {
    state = userTaskState;
  }

  @Override
  public void applyState(final long key, final UserTaskRecordValue value) {

  }
}
