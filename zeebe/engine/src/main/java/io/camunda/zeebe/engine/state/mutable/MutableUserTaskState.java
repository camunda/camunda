/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.UserTaskIntermediateStateValue;
import io.camunda.zeebe.engine.state.instance.UserTaskRecordRequestMetadata;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.function.Consumer;

public interface MutableUserTaskState extends UserTaskState {

  void create(final UserTaskRecord userTask);

  void update(final UserTaskRecord userTask);

  void updateUserTaskLifecycleState(final long userTaskKey, final LifecycleState newLifecycleState);

  void delete(final long userTaskKey);

  void storeIntermediateState(final UserTaskRecord userTask, final LifecycleState lifecycleState);

  void updateIntermediateState(long key, Consumer<UserTaskIntermediateStateValue> updater);

  void deleteIntermediateState(final long userTaskKey);

  void deleteIntermediateStateIfExists(final long userTaskKey);

  void storeRecordRequestMetadata(
      final long userTaskKey, final UserTaskRecordRequestMetadata recordRequestMetadata);

  void deleteRecordRequestMetadata(final long userTaskKey);
}
