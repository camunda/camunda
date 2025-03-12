/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskCanceledApplierTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCanceledApplier userTaskCanceledApplier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskCanceledApplier = new UserTaskCanceledApplier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldCleanIntermediateStateWhenUserTaskCancelled() {
    // given
    final var userTaskKey = 1;

    // Initial state of the User Task
    final var initialState =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("initial_user"));

    // Apply initial task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, initialState);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, initialState);

    // Simulate an update event with a change
    final var updateAttempt =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("update_user"))
            .setCandidateUsersChanged();
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.UPDATING, updateAttempt);

    // Ensure the intermediate state is present has the new change
    assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord())
        .describedAs("Expect that intermediate state holds the pending update")
        .hasCandidateUsersList("update_user")
        .hasNoCandidateGroupsList()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasPriority(50);

    // when
    userTaskCanceledApplier.applyState(userTaskKey, initialState);

    // then
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs(
            "Expect that intermediate state is cleared after cancellation of the User Task")
        .isNull();

    assertThat(userTaskState.findRecordRequestMetadata(userTaskKey))
        .describedAs("Expect that request metadata is cleared after cancellation of the User Task")
        .isEmpty();

    Assertions.assertThat(userTaskState.getUserTask(userTaskKey))
        .describedAs("Expect that state is cleared after cancellation of the User Task")
        .isNull();
  }
}
