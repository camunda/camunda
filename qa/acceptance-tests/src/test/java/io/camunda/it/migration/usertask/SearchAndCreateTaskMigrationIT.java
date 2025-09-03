/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@TestMethodOrder(OrderAnnotation.class)
@Disabled // TODO: Test to be deleted
public class SearchAndCreateTaskMigrationIT extends UserTaskMigrationHelper {

  private static final Instant STARTING_INSTANT = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  private static final int ARCHIVING_WAITING_PERIOD_SECONDS = 10;

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withBeforeUpgradeConsumer(
              (db, migrator) -> {
                setup(db, migrator, null);
                completeUserTaskAndWaitForArchiving(
                    migrator, USER_TASK_KEYS.get("first"), ARCHIVING_WAITING_PERIOD_SECONDS * 3);
              })
          .withInitialEnvOverrides(
              Map.of(
                  "CAMUNDA_TASKLIST_ARCHIVER_WAITPERIODBEFOREARCHIVING",
                  ARCHIVING_WAITING_PERIOD_SECONDS + "s"));

  @Test
  @Order(1)
  void shouldReturnReindexedTasks(final CamundaMigrator migrator) {
    final var searchResponse = migrator.getCamundaClient().newUserTaskSearchRequest().send().join();

    assertThat(searchResponse.page().totalItems()).isEqualTo(2);
    assertThat(searchResponse.items()).hasSize(2);
    searchResponse
        .items()
        .forEach(
            item -> {
              assertThat(item)
                  .usingRecursiveComparison()
                  .ignoringFields(
                      "userTaskKey",
                      "creationDate",
                      "completionDate",
                      "state",
                      "elementInstanceKey",
                      "processDefinitionKey",
                      "processInstanceKey")
                  .isEqualTo(
                      new UserTaskImpl(
                          new UserTaskResult()
                              .state(UserTaskStateEnum.CREATED)
                              .name("user-task")
                              .assignee(null)
                              .elementId("user-task")
                              .candidateGroups(List.of())
                              .candidateUsers(List.of())
                              .processDefinitionId("task-process")
                              .formKey(null)
                              .followUpDate(null)
                              .dueDate(null)
                              .tenantId("<default>")
                              .externalFormReference(null)
                              .processDefinitionVersion(2)
                              .customHeaders(Map.of())
                              .priority(50)));

              assertThat(List.of(USER_TASK_KEYS.get("first"), USER_TASK_KEYS.get("second")))
                  .contains(item.getUserTaskKey());
              assertThat(item.getElementInstanceKey()).isNotNull();
              assertThat(item.getProcessDefinitionKey()).isNotNull();
              assertThat(item.getProcessInstanceKey()).isNotNull();

              assertThat(item.getCreationDate()).isNotNull();
              final var creationInstant = Instant.parse(item.getCreationDate());
              assertThat(creationInstant).isAfter(STARTING_INSTANT);

              if (item.getUserTaskKey().equals(USER_TASK_KEYS.get("first"))) {
                assertThat(item.getState()).isEqualTo(UserTaskState.COMPLETED);
                assertThat(item.getCompletionDate()).isNotNull();
                final var completionInstant = Instant.parse(item.getCompletionDate());
                assertThat(completionInstant).isAfter(creationInstant);
              } else {
                assertThat(item.getState()).isEqualTo(UserTaskState.CREATED);
                assertThat(item.getCompletionDate()).isNull();
              }
            });
  }

  @Test
  @Order(2)
  void shouldCreateNewTask(final CamundaMigrator migrator) {
    final var zeebeProcessDefinitionKey =
        deployProcess(migrator.getCamundaClient(), t -> t.zeebeUserTask().zeebeAssignee(null));

    final var processInstanceKey =
        startProcessInstance(migrator.getCamundaClient(), zeebeProcessDefinitionKey);

    Awaitility.await("wait until new user task is available")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var searchResponse =
                  migrator
                      .getCamundaClient()
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();

              assertThat(searchResponse.items()).hasSize(1);
            });
  }
}
