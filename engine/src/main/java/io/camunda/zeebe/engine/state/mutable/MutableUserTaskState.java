/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.function.Consumer;

public interface MutableUserTaskState extends UserTaskState {

  void create(final UserTaskRecord userTask);

  void update(final UserTaskRecord userTask);

  void update(final long userTaskKey, final Consumer<UserTaskRecord> modifier);

  void updateUserTaskLifecycleState(final long userTaskKey, final LifecycleState newLifecycleState);

  void delete(final long userTaskKey);
}
