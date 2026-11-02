/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.state.instance.UserTaskIntermediateStateValue;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;
import java.util.function.LongPredicate;

public interface UserTaskState {

  LifecycleState getLifecycleState(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey, final TenantAccess authorizations);

  UserTaskIntermediateStateValue getIntermediateState(final long userTaskKey);

  Optional<String> findInitialAssignee(long key);

  boolean isSuspended(long processInstanceKey, long userTaskKey);

  boolean hasSuspendedUserTasks(long processInstanceKey);

  /**
   * Visits the keys of the suspended user tasks of the given process instance, in ascending order.
   *
   * @param processInstanceKey the process instance whose suspended user tasks to visit
   * @param startAtUserTaskKey the user task key to start at (inclusive), or a negative value to
   *     start at the beginning
   * @param visitor called with each user task key; returning {@code false} stops the iteration
   */
  void forEachSuspendedUserTask(
      long processInstanceKey, long startAtUserTaskKey, LongPredicate visitor);

  enum LifecycleState {
    NOT_FOUND((byte) 0),
    CREATING((byte) 1),
    CREATED((byte) 2),

    COMPLETING((byte) 3),
    CANCELING((byte) 4),

    ASSIGNING((byte) 5),

    UPDATING((byte) 6),

    CLAIMING((byte) 7);

    final byte value;

    LifecycleState(final byte value) {
      this.value = value;
    }
  }
}
