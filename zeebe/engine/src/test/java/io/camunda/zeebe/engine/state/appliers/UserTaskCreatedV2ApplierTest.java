/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.msgpack.value.StringValue.EMPTY_STRING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskCreatedV2ApplierTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCreatedV2Applier userTaskCreatedV2Applier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskCreatedV2Applier = new UserTaskCreatedV2Applier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  void shouldTransitionUserTaskLifecycleToCreatedOnApply() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // simulate a user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);

    // verify initial state
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to be in CREATING state before applying CREATED")
        .isEqualTo(LifecycleState.CREATING);

    // when
    userTaskCreatedV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CREATED state")
        .isEqualTo(LifecycleState.CREATED);
  }

  @Test
  public void shouldCleanIntermediateStateButNotAssigneeWhenUserTaskCreated() {
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final String initialAssignee = "initial_assignee";

    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee(initialAssignee)
            .setElementInstanceKey(elementInstanceKey);

    // simulate a user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);

    // when
    userTaskCreatedV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expect that intermediate state is cleared")
        .isNull();

    assertThat(userTaskState.getInitialAssignee(userTaskKey))
        .describedAs("Expect that intermediate assignee is not cleared")
        .isEqualTo(initialAssignee);
  }

  @Test
  public void shouldUpdateUserTaskWithCorrectionsWhenUserTaskCreated() {
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    // user task record with assignee set
    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee("initial_assignee")
            .setElementInstanceKey(elementInstanceKey);

    // simulate a user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);

    // simulate corrections
    testSetup.applyEventToState(
        userTaskKey,
        UserTaskIntent.CORRECTED,
        userTaskRecord
            .setCandidateGroupsList(List.of("overwritten"))
            .setCandidateGroupsChanged()
            .setCandidateUsersList(List.of("overwritten"))
            .setCandidateUsersChanged()
            .setDueDate("overwritten")
            .setDueDateChanged()
            .setFollowUpDate("overwritten")
            .setFollowUpDateChanged()
            .setPriority(99)
            .setPriorityChanged());

    // when
    userTaskCreatedV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    Assertions.assertThat(userTaskState.getUserTask(userTaskKey))
        .describedAs(
            "Expect that user task was updated and ensure corrections were stored. "
                + "Expect that the changed attributes are not persisted. "
                + "Expect that user task has not yet been assigned.")
        .hasAssignee(EMPTY_STRING)
        .hasCandidateUsersList(List.of("overwritten"))
        .hasCandidateGroupsList(List.of("overwritten"))
        .hasDueDate("overwritten")
        .hasFollowUpDate("overwritten")
        .hasPriority(99)
        .hasNoChangedAttributes();
  }
}
