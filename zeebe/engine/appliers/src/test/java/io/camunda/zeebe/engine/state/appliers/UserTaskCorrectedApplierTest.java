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
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskCorrectedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCorrectedApplier userTaskCorrectedApplier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskCorrectedApplier = new UserTaskCorrectedApplier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  /**
   * Returns a stream of test case arguments. In order, these arguments are:
   *
   * <ul>
   *   <li>the user task key
   *   <li>a list of intents to apply to the state before applying the corrected event
   *   <li>the expected lifecycle state after applying the corrected event
   * </ul>
   */
  public static Stream<Arguments> testCases() {
    return Stream.of(
        Arguments.of(1, List.of(), LifecycleState.CREATING),
        Arguments.of(
            2, List.of(UserTaskIntent.CREATING, UserTaskIntent.CREATED), LifecycleState.ASSIGNING),
        Arguments.of(
            3, List.of(UserTaskIntent.CREATING, UserTaskIntent.CREATED), LifecycleState.UPDATING),
        Arguments.of(
            4, List.of(UserTaskIntent.CREATING, UserTaskIntent.CREATED), LifecycleState.COMPLETING),
        Arguments.of(
            5, List.of(UserTaskIntent.CREATING, UserTaskIntent.CREATED), LifecycleState.CLAIMING));
  }

  @ParameterizedTest(name = "on {2}")
  @MethodSource("testCases")
  void shouldCorrectIntermediateUserTaskDataOnIntent(
      final long userTaskKey, final List<UserTaskIntent> setup, final LifecycleState state) {
    // given
    final var given =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee("initial")
            .setCandidateGroupsList(List.of("initial"))
            .setCandidateUsersList(List.of("initial"))
            .setDueDate("initial")
            .setFollowUpDate("initial")
            .setPriority(1);

    setup.forEach(setupIntent -> testSetup.applyEventToState(userTaskKey, setupIntent, given));
    testSetup.applyEventToState(userTaskKey, mapLifecycleStateToIntent(state), given);

    Assumptions.assumeThat(userTaskState.getUserTask(userTaskKey)).isNotNull();
    Assumptions.assumeThat(userTaskState.getLifecycleState(userTaskKey)).isEqualTo(state);
    Assumptions.assumeThat(userTaskState.getIntermediateState(userTaskKey)).isNotNull();

    // when
    userTaskCorrectedApplier.applyState(
        userTaskKey,
        given
            .setAssignee("overwritten")
            .setCandidateGroupsList(List.of("overwritten"))
            .setCandidateUsersList(List.of("overwritten"))
            .setDueDate("overwritten")
            .setFollowUpDate("overwritten")
            .setPriority(99)
            .setCandidateGroupsChanged()
            .setCandidateUsersChanged()
            .setDueDateChanged()
            .setFollowUpDateChanged()
            .setPriorityChanged());

    // then
    Assertions.assertThat(userTaskState.getIntermediateState(userTaskKey).getRecord())
        .describedAs("Expect that intermediate state is updated")
        .isEqualTo(
            new UserTaskRecord()
                .setUserTaskKey(userTaskKey)
                .setAssignee("overwritten")
                .setCandidateGroupsList(List.of("overwritten"))
                .setCandidateUsersList(List.of("overwritten"))
                .setDueDate("overwritten")
                .setFollowUpDate("overwritten")
                .setPriority(99))
        .satisfies(
            intermediateState ->
                Assertions.assertThat(intermediateState.getChangedAttributes())
                    .describedAs("Expect that the changed attributes are not persisted")
                    .isEmpty());
    Assertions.assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expect that lifecycle state is not changed")
        .isEqualTo(state);
  }

  private static UserTaskIntent mapLifecycleStateToIntent(final LifecycleState state) {
    return switch (state) {
      case CREATING -> UserTaskIntent.CREATING;
      case ASSIGNING -> UserTaskIntent.ASSIGNING;
      case CLAIMING -> UserTaskIntent.CLAIMING;
      case UPDATING -> UserTaskIntent.UPDATING;
      case CANCELING -> UserTaskIntent.CANCELING;
      case COMPLETING -> UserTaskIntent.COMPLETING;
      default ->
          throw new IllegalArgumentException(
              "Unexpected lifecycle state %s received".formatted(state));
    };
  }
}
