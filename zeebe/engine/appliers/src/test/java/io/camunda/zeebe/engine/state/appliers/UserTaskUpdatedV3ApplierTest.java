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
public class UserTaskUpdatedV3ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskUpdatedV3Applier userTaskUpdatedV3Applier;

  /** Used for state */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskUpdatedV3Applier = new UserTaskUpdatedV3Applier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldClearChangedAttributesOnPersistedUserTaskAfterUpdate() {
    // given
    final long userTaskKey = 1;

    // Initial state of the user task before the update
    final var initialState =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("initial_user"))
            .setPriority(40);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, initialState);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, initialState);

    // An update carrying a set of changed attributes
    final var update =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setCandidateUsersList(List.of("updated_user"))
            .setPriority(85)
            .setCandidateUsersChanged()
            .setPriorityChanged();
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.UPDATING, update);

    Assertions.assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord())
        .describedAs("Expect the pending update to track its changed attributes")
        .hasOnlyChangedAttributes(UserTaskRecord.CANDIDATE_USERS, UserTaskRecord.PRIORITY);

    // when
    userTaskUpdatedV3Applier.applyState(userTaskKey, update);

    // then - the applier persists the new values but clears the changed-attributes tracking, so the
    // stored user task carries no leftover change set from the update.
    Assertions.assertThat(userTaskState.getUserTask(userTaskKey))
        .describedAs("Expect the update to be persisted with its changed attributes cleared")
        .hasCandidateUsersList("updated_user")
        .hasPriority(85)
        .hasNoChangedAttributes();

    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expect lifecycle state to return to 'CREATED' after the update")
        .isEqualTo(LifecycleState.CREATED);
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expect the intermediate update state to be cleared")
        .isNull();
  }
}
