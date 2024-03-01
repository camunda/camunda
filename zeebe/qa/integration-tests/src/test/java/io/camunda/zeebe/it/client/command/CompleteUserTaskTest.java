/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class CompleteUserTaskTest extends UserTaskTest {

  @Test
  public void shouldCompleteUserTask() {
    // when
    client.newUserTaskCompleteCommand(userTaskKey).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("complete");
        });
  }

  @Test
  public void shouldCompleteUserTaskWithAction() {
    // when
    client.newUserTaskCompleteCommand(userTaskKey).action("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("foo");
        });
  }

  @Test
  public void shouldCompleteUserTaskWithVariables() {
    // when

    client.newUserTaskCompleteCommand(userTaskKey).variables(Map.of("foo", "bar")).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> assertThat(userTask.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  public void shouldRejectIfJobIsAlreadyCompleted() {
    // given
    client.newUserTaskCompleteCommand(userTaskKey).send().join();

    // when / then
    assertThatThrownBy(() -> client.newUserTaskCompleteCommand(userTaskKey).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
