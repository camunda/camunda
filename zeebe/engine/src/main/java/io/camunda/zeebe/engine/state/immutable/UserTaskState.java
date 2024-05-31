/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Map;

public interface UserTaskState {

  LifecycleState getLifecycleState(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey, final Map<String, Object> authorizations);

  enum LifecycleState {
    NOT_FOUND((byte) 0),
    CREATING((byte) 1),
    CREATED((byte) 2),

    COMPLETING((byte) 3),
    CANCELING((byte) 4),

    ASSIGNING((byte) 5),

    UPDATING((byte) 6);

    final byte value;

    LifecycleState(final byte value) {
      this.value = value;
    }

    public Intent toIntent() {
      return switch (this) {
        case NOT_FOUND -> UserTaskIntent.UNKNOWN;
        case CREATING -> UserTaskIntent.CREATING;
        case CREATED -> UserTaskIntent.CREATED;
        case COMPLETING -> UserTaskIntent.COMPLETING;
        case CANCELING -> UserTaskIntent.CANCELING;
        case ASSIGNING -> UserTaskIntent.ASSIGNING;
        case UPDATING -> UserTaskIntent.UPDATING;
      };
    }
  }
}
