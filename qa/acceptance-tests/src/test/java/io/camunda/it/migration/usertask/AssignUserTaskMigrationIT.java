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
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AssignUserTaskMigrationIT extends UserTaskMigrationHelper {

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withBeforeUpgradeConsumer((db, migrator) -> setup(db, migrator, null));

  @Test
  void shouldAssign87ZeebeTaskV1(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("first");
    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldAssign87ZeebeTaskV2(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("second");

    migrator.getCamundaClient().newUserTaskAssignCommand(taskKey).assignee("demo").send().join();

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldAssign88ZeebeTaskV1(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.ZEEBE_USER_TASK));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldAssign88ZeebeTaskV2(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.ZEEBE_USER_TASK));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

    migrator.getCamundaClient().newUserTaskAssignCommand(taskKey).assignee("demo").send().join();

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldAssign87JobWorkerV1(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("third");
    final var res =
        migrator
            .getTasklistClient()
            .withAuthentication("demo", "demo")
            .assignUserTask(taskKey, "demo");
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeAssigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldAssign88JobWorkerV1(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.JOB_WORKER));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

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
                      .newUserTaskSearchRequest()
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
