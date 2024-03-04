/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import org.junit.jupiter.api.Test;

public final class UnassignUserTaskTest extends UserTaskTest {

  @Test
  public void shouldUnassignUserTask() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when
    client.newUserTaskUnassignCommand(userTaskKey).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey,
        2,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("unassign");
        });
  }
}
