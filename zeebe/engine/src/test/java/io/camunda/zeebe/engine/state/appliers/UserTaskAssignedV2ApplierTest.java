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
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskAssignedV2ApplierTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskAssignedV2Applier userTaskAssignedV2Applier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskAssignedV2Applier = new UserTaskAssignedV2Applier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldTransitionUserTaskLifecycleToCreatedOnApply() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final String assignee = "initial_assignee";

    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee(assignee)
            .setElementInstanceKey(elementInstanceKey);

    // assignee is present in the creating event
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(
        userTaskKey, UserTaskIntent.CREATED, userTaskRecord.unsetAssignee());
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);

    // when
    userTaskAssignedV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CREATED state")
        .isEqualTo(LifecycleState.CREATED);
  }

  @Test
  public void shouldClearIntermediateStateAndAssigneeWhenUserTaskAssigned() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final String initialAssignee = "initial_assignee";

    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee(initialAssignee)
            .setElementInstanceKey(elementInstanceKey);

    // assignee is present in the creating event
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(
        userTaskKey, UserTaskIntent.CREATED, userTaskRecord.unsetAssignee());
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);

    assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord().getAssignee())
        .describedAs("Expect intermediate user task to be stored without assignee")
        .isEmpty();

    assertThat(userTaskState.getIntermediateAssignee(userTaskKey))
        .describedAs("Expect intermediate assignee to be present")
        .isEqualTo(initialAssignee);

    // when
    userTaskAssignedV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expect that intermediate state to be cleaned up")
        .isNull();

    assertThat(userTaskState.getIntermediateAssignee(userTaskKey))
        .describedAs("Expect intermediate assignee to be cleaned up")
        .isNull();
  }
}
