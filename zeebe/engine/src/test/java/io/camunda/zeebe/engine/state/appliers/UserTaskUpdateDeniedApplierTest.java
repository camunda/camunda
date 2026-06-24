/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskUpdateDeniedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskUpdateDeniedApplier userTaskUpdateDeniedApplier;

  /** Used for state */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskUpdateDeniedApplier = new UserTaskUpdateDeniedApplier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldRevertUpdatesToTheTaskIfTransitionWasDenied() {
    // given
    final long userTaskKey = 1;
    final int initialPriority = 40;
    final int newPriority = 85;

    // Initial state of the User Task before an update attempt
    final var initialState =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("initial_user"))
            .setCandidateGroupsList(List.of("initial_group"))
            .setFollowUpDate("initial_follow_up_date")
            .setDueDate("initial_due_date")
            .setPriority(initialPriority);

    // Apply initial task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, initialState);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, initialState);

    // Simulate an update event with changes
    final var updateAttempt =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("update_user"))
            .setDueDate("update_due_date")
            .setPriority(newPriority)
            .setCandidateUsersChanged()
            .setDueDateChanged()
            .setPriorityChanged();
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.UPDATING, updateAttempt);

    // Ensure the state has the new changes
    Assertions.assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord())
        .describedAs("Expect that intermediate state holds the pending update")
        .hasCandidateUsersList("update_user")
        .hasDueDate("update_due_date")
        .hasPriority(newPriority)
        .hasNoCandidateGroupsList()
        .hasFollowUpDate("");

    // Ensure that the actual user task state hasn't been updated yet
    assertThat(userTaskState.getUserTask(userTaskKey))
        .describedAs(
            "Expect the actual user task state to remain unchanged while the update is in progress")
        .isEqualTo(initialState);

    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expect lifecycle state to be 'UPDATING' before denial")
        .isEqualTo(LifecycleState.UPDATING);

    // when
    userTaskUpdateDeniedApplier.applyState(userTaskKey, initialState);

    // then
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expect that intermediate state is cleared after denial")
        .isNull();

    assertThat(userTaskState.getUserTask(userTaskKey))
        .describedAs("Expect user task to retain initial values after update was denied")
        .isEqualTo(initialState);

    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expect lifecycle state to be reverted to 'CREATED' after denial")
        .isEqualTo(LifecycleState.CREATED);
  }
}
