/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class UserTaskSuspendedApplierTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long USER_TASK_KEY = 1L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableUserTaskState userTaskState;
  private AppliersTestSetupHelper testSetup;
  private UserTaskRecord userTask;

  @BeforeEach
  void setup() {
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
    userTask =
        new UserTaskRecord()
            .setUserTaskKey(USER_TASK_KEY)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.CREATING, userTask);
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.CREATED, userTask);
  }

  @Test
  void shouldTrackUserTaskAsSuspended() {
    // when
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.SUSPENDED, userTask);

    // then
    assertThat(userTaskState.isSuspended(PROCESS_INSTANCE_KEY, USER_TASK_KEY)).isTrue();
  }

  @Test
  void shouldStopTrackingUserTaskWhenResumed() {
    // given
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.SUSPENDED, userTask);

    // when
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.RESUMED, userTask);

    // then
    assertThat(userTaskState.isSuspended(PROCESS_INSTANCE_KEY, USER_TASK_KEY)).isFalse();
  }

  @Test
  void shouldStopTrackingUserTaskWhenCanceledWhileSuspended() {
    // given - a canceled suspended instance never resumes, so cancellation is its only cleanup
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.SUSPENDED, userTask);

    // when
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.CANCELING, userTask);
    testSetup.applyEventToState(USER_TASK_KEY, UserTaskIntent.CANCELED, userTask);

    // then
    assertThat(userTaskState.isSuspended(PROCESS_INSTANCE_KEY, USER_TASK_KEY)).isFalse();
  }
}
