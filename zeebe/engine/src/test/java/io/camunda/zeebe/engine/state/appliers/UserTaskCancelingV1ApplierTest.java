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
public class UserTaskCancelingV1ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCancelingV1Applier userTaskCancelingApplier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskCancelingApplier = new UserTaskCancelingV1Applier(processingState);
    userTaskState = processingState.getUserTaskState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  void shouldTransitionUserTaskLifecycleToCancelingOnApply() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // simulate a user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);

    // verify initial state
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to be in CREATED state before applying CANCELING")
        .isEqualTo(LifecycleState.CREATED);

    // when
    userTaskCancelingApplier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CANCELING state")
        .isEqualTo(LifecycleState.CANCELING);
  }
}
