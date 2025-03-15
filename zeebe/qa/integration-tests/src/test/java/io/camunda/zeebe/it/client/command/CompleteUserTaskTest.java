/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class CompleteUserTaskTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withSchemaCreationDisabled();

  @AutoClose private CamundaClient client;

  private long userTaskKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
    userTaskKey = resourcesHelper.createSingleUserTask();
  }

  @Test
  void shouldCompleteUserTask() {
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
  void shouldCompleteUserTaskWithAction() {
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
  void shouldCompleteUserTaskWithVariables() {
    // when

    client.newUserTaskCompleteCommand(userTaskKey).variables(Map.of("foo", "bar")).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> assertThat(userTask.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  void shouldRejectIfJobIsAlreadyCompleted() {
    // given
    client.newUserTaskCompleteCommand(userTaskKey).send().join();

    // when / then
    assertThatThrownBy(() -> client.newUserTaskCompleteCommand(userTaskKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
