/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class AssignUserTaskTest {

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  private long userTaskKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
    userTaskKey = resourcesHelper.createSingleUserTask();
  }

  @Test
  void shouldAssignUserTaskWithAssignee() {
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
  void shouldAssignUserTaskWithAssigneeWithImplicitAllowOverride() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when
    client.newUserTaskAssignCommand(userTaskKey).assignee("barbar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getAssignee()).isEqualTo("barbar"));
  }

  @Test
  void shouldAssignUserTaskWithAssigneeWithExplicitAllowOverride() {
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
  void shouldAssignUserTaskWithAssigneeAndAction() {
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
  void shouldRejectIfUserTaskIsAlreadyAssignedWithProhibitedOverride() {
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
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldRejectIfMissingAssignee() {
    // when / then
    assertThatThrownBy(() -> client.newUserTaskAssignCommand(userTaskKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  void shouldRejectIfMissingAssigneeWithProhibitedOverride() {
    // when / then
    assertThatThrownBy(
            () -> client.newUserTaskAssignCommand(userTaskKey).allowOverride(false).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
