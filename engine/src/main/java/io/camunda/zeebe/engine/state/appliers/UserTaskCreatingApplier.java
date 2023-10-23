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
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class UserTaskCreatingApplier implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState state;

  public UserTaskCreatingApplier(final MutableUserTaskState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    // on user task creating

    state.create(key, value);
  }
}
