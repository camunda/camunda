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
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CompleteUserTaskMigrationIT extends UserTaskMigrationHelper {

  @RegisterExtension static final MigrationITExtension PROVIDER = new MigrationITExtension();

  @TestTemplate
  void shouldComplete87ZeebeTaskV1(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee("demo"));

    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);

    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldComplete87ZeebeTaskV2(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee("demo"));
    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);

    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());

    final var res =
        PROVIDER.getCamundaClient(context).newUserTaskCompleteCommand(taskKey).send().join();

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldComplete88ZeebeTaskV1(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee("demo"));
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldComplete88ZeebeTaskV2(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());

    final var piKey =
        deployAndStartUserTaskProcess(
            migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee("demo"));
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    PROVIDER.getCamundaClient(context).newUserTaskCompleteCommand(taskKey).send().join();

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldComplete87JobWorkerV1(final ExtensionContext context, final CamundaMigrator migrator)
      throws IOException {

    final var piKey =
        deployAndStartUserTaskProcess(migrator.getCamundaClient(), t -> t.zeebeAssignee("demo"));
    final var taskKey = waitForTaskToBeImportedReturningId(migrator, piKey);

    PROVIDER.has87Data(context);
    PROVIDER.upgrade(context, new HashMap<>());

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  @TestTemplate
  void shouldComplete88JobWorkerV1(final ExtensionContext context, final CamundaMigrator migrator) {

    PROVIDER.upgrade(context, new HashMap<>());

    final var piKey =
        deployAndStartUserTaskProcess(migrator.getCamundaClient(), t -> t.zeebeAssignee("demo"));
    final var taskKey = waitFor88TaskToBeImportedReturningId(migrator, piKey);

    final var res =
        migrator.getTasklistClient().withAuthentication("demo", "demo").completeUserTask(taskKey);
    assertThat(res.statusCode()).isEqualTo(200);

    shouldBeCompleted(migrator.getCamundaClient(), taskKey);
  }

  private void shouldBeCompleted(final CamundaClient client, final long taskKey) {
    Awaitility.await("User Task should be completed")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var tasks =
                  client
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskKey(taskKey))
                      .send()
                      .join()
                      .items();
              if (!tasks.isEmpty()) {
                assertThat(tasks.getFirst().getState().name()).isEqualTo("COMPLETED");
              }
            });
  }
}
