/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AssignUserTaskMigrationIT extends UserTaskMigrationHelper {
  @RegisterExtension static final MigrationITExtension PROVIDER = new MigrationITExtension();

  @TestTemplate
  void shouldAssign87ZeebeTaskV1(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey =
        deployAndStartUserTaskProcess(
            PROVIDER.getCamundaClient(context), AbstractUserTaskBuilder::zeebeUserTask);
    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);
    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());
    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldAssign87ZeebeTaskV2(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), AbstractUserTaskBuilder::zeebeUserTask);
    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);

    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());

    migrator.getCamundaClient().newUserTaskAssignCommand(taskKey).assignee("demo").send().join();

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldAssign88ZeebeTaskV1(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), AbstractUserTaskBuilder::zeebeUserTask);
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldAssign88ZeebeTaskV2(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), AbstractUserTaskBuilder::zeebeUserTask);
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    migrator.getCamundaClient().newUserTaskAssignCommand(taskKey).assignee("demo").send().join();

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldAssign87JobWorkerV1(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey = deployAndStartUserTaskProcess(migrator.getCamundaClient(), t -> t);
    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);
    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());

    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldAssign88JobWorkerV1(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());
    final var piKey = deployAndStartUserTaskProcess(migrator.getCamundaClient(), t -> t);
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  private void shouldBeAssigned(final CamundaClient client, final long userTaskKey) {
    Awaitility.await("User Task should be assigned")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var tasks =
                  client
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(userTaskKey))
                      .send()
                      .join()
                      .items();
              if (!tasks.isEmpty()) {
                assertThat(tasks.getFirst().getAssignee()).isEqualTo("demo");
              }
            });
  }
}
