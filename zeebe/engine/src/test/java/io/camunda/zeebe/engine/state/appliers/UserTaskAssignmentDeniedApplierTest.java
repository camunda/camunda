/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskAssignmentDeniedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskAssignmentDeniedApplier userTaskAssignmentDeniedApplierApplier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private TestSetup testSetup;

  @BeforeEach
  public void setup() {
    userTaskAssignmentDeniedApplierApplier = new UserTaskAssignmentDeniedApplier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new TestSetup(processingState);
  }

  @Test
  public void shouldRevertAssigneeIfAssignmentDeniedByTaskListener() {
    // given
    final var userTaskKey = 1;
    final var initialAssignee = "initial";
    final var newAssignee = "changed";

    final var given = new UserTaskRecord().setUserTaskKey(userTaskKey);

    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, given);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, given);

    testSetup.applyEventToState(
        userTaskKey, UserTaskIntent.ASSIGNING, given.setAssignee(initialAssignee));
    testSetup.applyEventToState(
        userTaskKey, UserTaskIntent.ASSIGNED, given.setAssignee(initialAssignee));

    testSetup.applyEventToState(
        userTaskKey, UserTaskIntent.ASSIGNING, given.setAssignee(newAssignee));

    Assertions.assertThat(userTaskState.getUserTask(userTaskKey).getAssignee())
        .isEqualTo(initialAssignee);
    Assertions.assertThat(userTaskState.getLifecycleState(userTaskKey))
        .isEqualTo(LifecycleState.ASSIGNING);

    // when
    userTaskAssignmentDeniedApplierApplier.applyState(userTaskKey, given.setAssignee(newAssignee));

    // then
    Assertions.assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expect that intermediate state is not present anymore")
        .isNull();
    Assertions.assertThat(userTaskState.findRecordRequestMetadata(userTaskKey))
        .describedAs("Expect that record request metadata is not present anymore")
        .isEmpty();
    Assertions.assertThat(userTaskState.getUserTask(userTaskKey).getAssignee())
        .describedAs("Expect that user task assignee has not been updated")
        .isEqualTo(initialAssignee);
    Assertions.assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expect that lifecycle state is reverted to 'CREATED'")
        .isEqualTo(LifecycleState.CREATED);
  }

  private static final class TestSetup {

    private final EventAppliers eventAppliers;

    TestSetup(final MutableProcessingState processingState) {
      eventAppliers = new EventAppliers();
      eventAppliers.registerEventAppliers(processingState);
    }

    /**
     * Applies the event of the given intent to the state.
     *
     * @implNote applies the event using the latest version of the record.
     * @param intent the intent of the event to apply
     * @param userTaskRecord data of the event to apply
     */
    private void applyEventToState(
        final long userTaskKey, final UserTaskIntent intent, final UserTaskRecord userTaskRecord) {
      final int latestVersion = eventAppliers.getLatestVersion(intent);
      eventAppliers.applyState(userTaskKey, intent, userTaskRecord, latestVersion);
    }
  }
}
