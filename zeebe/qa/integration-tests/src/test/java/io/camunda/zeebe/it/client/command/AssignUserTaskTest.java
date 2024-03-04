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

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import org.junit.jupiter.api.Test;

public final class AssignUserTaskTest extends UserTaskTest {

  @Test
  public void shouldAssignUserTaskWithAssignee() {
    // when
    client.newUserTaskAssignCommand(userTaskKey).assignee("barbar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo("barbar");
          assertThat(userTask.getAction()).isEqualTo("assign");
        });
  }

  @Test
  public void shouldAssignUserTaskWithAssigneeWithImplicitAllowOverride() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when
    client.newUserTaskAssignCommand(userTaskKey).assignee("barbar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getAssignee()).isEqualTo("barbar"));
  }

  @Test
  public void shouldAssignUserTaskWithAssigneeWithExplicitAllowOverride() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when
    client
        .newUserTaskAssignCommand(userTaskKey)
        .assignee("barbar")
        .allowOverride(true)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getAssignee()).isEqualTo("barbar"));
  }

  @Test
  public void shouldAssignUserTaskWithAssigneeAndAction() {
    // when
    client.newUserTaskAssignCommand(userTaskKey).assignee("barbar").action("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo("barbar");
          assertThat(userTask.getAction()).isEqualTo("foo");
        });
  }

  @Test
  public void shouldRejectIfJobIsAlreadyAssignedWithProhibitedOverride() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserTaskAssignCommand(userTaskKey)
                    .assignee("barbar")
                    .allowOverride(false)
                    .send()
                    .join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  public void shouldRejectIfMissingAssignee() {
    // when / then
    assertThatThrownBy(() -> client.newUserTaskAssignCommand(userTaskKey).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  public void shouldRejectIfMissingAssigneeWithProhibitedOverride() {
    // when / then
    assertThatThrownBy(
            () -> client.newUserTaskAssignCommand(userTaskKey).allowOverride(false).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
