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
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskCreatingV2ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCreatingV2Applier userTaskCreatingV2Applier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  @BeforeEach
  public void setup() {
    userTaskCreatingV2Applier = new UserTaskCreatingV2Applier(processingState);
    userTaskState = processingState.getUserTaskState();
  }

  @Test
  void shouldTransitionUserTaskLifecycleToCreatingOnApply() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // when
    userTaskCreatingV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CREATING state")
        .isEqualTo(LifecycleState.CREATING);
  }

  @Test
  public void shouldStoreIntermediateStateAndInitialAssigneeWhenCreatingUserTask() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final String initialAssignee = "initial_assignee";

    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee(initialAssignee)
            .setElementInstanceKey(elementInstanceKey);

    // when
    userTaskCreatingV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    // ensure the intermediate state is present without an assignee
    assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord().getAssignee())
        .describedAs("Expect that intermediate state to be present")
        .isEmpty();

    // ensure the intermediate assignee is stored
    assertThat(userTaskState.findInitialAssignee(userTaskKey))
        .describedAs("Expect initial assignee to be present")
        .isEqualTo(Optional.of(initialAssignee));
  }

  @Test
  public void
      shouldStoreIntermediateStateAndNoInitialAssigneeWhenCreatingUserTaskWithoutAssignee() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // when
    userTaskCreatingV2Applier.applyState(userTaskKey, userTaskRecord);

    // then
    // ensure the intermediate state is present without an assignee
    assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord().getAssignee())
        .describedAs("Expect that intermediate state to be present")
        .isEmpty();

    // ensure the intermediate assignee is not present
    assertThat(userTaskState.findInitialAssignee(userTaskKey))
        .describedAs("Expect initial assignee to not be present")
        .isEmpty();
  }
}
