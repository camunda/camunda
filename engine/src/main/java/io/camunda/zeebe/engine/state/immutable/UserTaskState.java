/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Map;

public interface UserTaskState {

  State getState(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey, final Map<String, Object> authorizations);

  enum State {
    NOT_FOUND((byte) 0),
    CREATING((byte) 1),
    CREATED((byte) 2),

    COMPLETING((byte) 3),
    COMPLETED((byte) 4),

    CANCELING((byte) 5);

    final byte value;

    State(final byte value) {
      this.value = value;
    }
  }
}
