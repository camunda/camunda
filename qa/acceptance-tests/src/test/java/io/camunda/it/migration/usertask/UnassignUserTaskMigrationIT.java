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
public class UnassignUserTaskMigrationIT extends UserTaskMigrationHelper {

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withBeforeUpgradeConsumer((db, migrator) -> setup(db, migrator, "demo"));

  @Test
  void shouldUnassign87ZeebeTaskV1(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("first");

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldUnassign87ZeebeTaskV2(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("second");

    migrator.getCamundaClient().newUserTaskUnassignCommand(taskKey).send().join();

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldUnassign88ZeebeTaskV1(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.ZEEBE_USER_TASK));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldUnassign88ZeebeTaskV2(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.ZEEBE_USER_TASK));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

    migrator.getCamundaClient().newUserTaskUnassignCommand(taskKey).send().join();

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldUnAssign87JobWorkerV1(final CamundaMigrator migrator) {

    final long taskKey = USER_TASK_KEYS.get("third");

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  @Test
  void shouldUnAssign88JobWorkerV1(final CamundaMigrator migrator) {

    final var piKey =
        startProcessInstance(
            migrator.getCamundaClient(),
            PROCESS_DEFINITION_KEYS.get(TaskImplementation.JOB_WORKER));
    final var taskKey = waitFor88CreatedTaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").unassignUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeUnassigned(migrator.getCamundaClient(), taskKey);
  }

  private void shouldBeUnassigned(final CamundaClient client, final long taskKey) {
    Awaitility.await("User Task should be unassigned")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var tasks =
                  client
                      .newUserTaskSearchRequest()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items();
              if (!tasks.isEmpty()) {
                assertThat(tasks.getFirst().getAssignee()).isNull();
              }
            });
  }
}
