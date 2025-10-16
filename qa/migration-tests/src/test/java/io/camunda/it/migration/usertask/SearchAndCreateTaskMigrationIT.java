/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.Profile;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
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
public class SearchAndCreateTaskMigrationIT extends UserTaskMigrationHelper {

  private static final Instant STARTING_INSTANT = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  private static final int ARCHIVING_WAITING_PERIOD_SECONDS = 10;
  private static final String RETENTION_AGE = "30s";

  @RegisterExtension
  static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withPostUpdateAdditionalProfiles(Profile.TASK_MIGRATION)
          .withBeforeUpgradeConsumer(
              (db, migrator) -> {
                setup(db, migrator, null);
                completeUserTaskAndWaitForArchiving(
                    migrator, USER_TASK_KEYS.get("first"), ARCHIVING_WAITING_PERIOD_SECONDS * 3);
                createEmpty87TaskDatedIndex(migrator);
              })
          .withInitialEnvOverrides(
              Map.of(
                  "CAMUNDA_TASKLIST_ARCHIVER_WAITPERIODBEFOREARCHIVING",
                  ARCHIVING_WAITING_PERIOD_SECONDS + "s"))
          .withUpgradeSystemPropertyOverrides(
              Map.of(
                  "camunda.migration.tasks.legacyIndexRetentionAge",
                  RETENTION_AGE,
                  "camunda.database.retention.enabled",
                  "true",
                  "camunda.database.retention.minimumAge",
                  "30d"));

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
              final var creationInstant = Instant.from(item.getCreationDate());
              assertThat(creationInstant).isAfter(STARTING_INSTANT);

              if (item.getUserTaskKey().equals(USER_TASK_KEYS.get("first"))) {
                assertThat(item.getState()).isEqualTo(UserTaskState.COMPLETED);
                assertThat(item.getCompletionDate()).isNotNull();
                final var completionInstant = Instant.from(item.getCompletionDate());
                assertThat(completionInstant).isAfter(creationInstant);
              } else {
                assertThat(item.getState()).isEqualTo(UserTaskState.CREATED);
                assertThat(item.getCompletionDate()).isNull();
              }
            });
  }

  @Test
  @Order(2)
  void shouldApplyLifecyclePolicyToDatedIndices() {
    Awaitility.await("wait until lifecycle policies are applied and initialized")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(3))
        .untilAsserted(this::assertAllDatedTaskIndicesHaveLifecyclePolicy);
  }

  @Test
  @Order(3)
  void shouldApplyLifecyclePolicyToRuntimeIndex() throws IOException, InterruptedException {
    final var indexName = String.format("%s-tasklist-task-8.5.0_", PROVIDER.getIndexPrefix());
    if (PROVIDER.isElasticSearch()) {
      assertThatIlmPolicyIsPresent();
      assertIndexHasIlmPolicy(indexName);
      assertIndexDeletionPolicyIsTriggered(indexName);
    } else {
      assertThatIsmPolicyIsPresent();
      assertIndexHasIsmPolicy(indexName);
      assertIndexDeletionPolicyIsTriggeredOpensearch(indexName);
    }
  }

  private void assertIndexDeletionPolicyIsTriggered(final String indexName) {
    final var uri =
        URI.create(String.format("%s/%s/_ilm/explain", PROVIDER.getDatabaseUrl(), indexName));
    Awaitility.await("wait until runtime index is deleted after retention period")
        .atMost(Duration.ofSeconds(200))
        .atLeast(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(uri)
                      .header("Content-Type", "application/json")
                      .GET()
                      .build();
              final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
              final var jsonResponse = OBJECT_MAPPER.readTree(response.body());
              assertThat(jsonResponse.at("/indices/" + indexName + "/phase").asText())
                  .isEqualTo("delete");
              assertThat(jsonResponse.at("/indices/" + indexName + "/action").asText())
                  .isEqualTo("delete");
            });
  }

  private void assertIndexDeletionPolicyIsTriggeredOpensearch(final String indexName) {
    final var uri =
        URI.create(
            String.format("%s/_plugins/_ism/explain/%s", PROVIDER.getDatabaseUrl(), indexName));
    Awaitility.await("wait until runtime index is deleted after retention period")
        .atMost(Duration.ofSeconds(200))
        .atLeast(Duration.ofSeconds(50))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(uri)
                      .header("Content-Type", "application/json")
                      .GET()
                      .build();
              final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
              final var jsonResponse = OBJECT_MAPPER.readTree(response.body());
              assertThat(jsonResponse.at("/" + indexName + "/transition_to").asText())
                  .isEqualTo("deleted");
            });
  }

  @Test
  @Order(4)
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

  private void assertAllDatedTaskIndicesHaveLifecyclePolicy() {
    if (PROVIDER.isElasticSearch()) {
      assertAllDatedTaskIndicesHaveIlmPolicy();
    } else {
      assertAllDatedTaskIndicesHaveIsmPolicy();
    }
  }

  private void assertAllDatedTaskIndicesHaveIlmPolicy() {
    final var datedIndices = getDatedTaskIndexNames();
    if (datedIndices.isEmpty()) {
      fail("Dated task indices were expected but not found");
    }
    datedIndices.forEach(this::assertIndexHasIlmPolicy);
  }

  private void assertIndexHasIlmPolicy(final String datedIndex) {
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      String.format("%s/%s/_settings", PROVIDER.getDatabaseUrl(), datedIndex)))
              .header("Content-Type", "application/json")
              .GET()
              .build();
      final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
      final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
      final var policy = jsonResponse.at("/" + datedIndex + "/settings/index/lifecycle");
      assertThat(policy).isNotNull();
      assertThat(policy.isMissingNode()).isFalse();
      assertThat(policy.get("name").asText()).isNotBlank();
    } catch (final Exception e) {
      fail("Failed to get ILM policy for index: " + datedIndex, e);
    }
  }

  private void assertAllDatedTaskIndicesHaveIsmPolicy() {
    final var datedIndices = getDatedTaskIndexNames();
    if (datedIndices.isEmpty()) {
      fail("Dated task indices were expected but not found");
    }
    datedIndices.forEach(this::assertIndexHasIsmPolicy);
  }

  private void assertIndexHasIsmPolicy(final String datedIndex) {
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      String.format(
                          "%s/_plugins/_ism/explain/%s", PROVIDER.getDatabaseUrl(), datedIndex)))
              .header("Content-Type", "application/json")
              .GET()
              .build();
      final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
      final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
      assertThat(jsonResponse.get(datedIndex)).isNotNull();
      assertThat(jsonResponse.get(datedIndex).get("index.plugins.index_state_management.policy_id"))
          .isNotNull();
      assertThat(jsonResponse.get(datedIndex).get("policy_id")).isNotNull();
    } catch (final Exception e) {
      fail("Failed to get ISM policy for index: " + datedIndex, e);
    }
  }

  private void assertThatIlmPolicyIsPresent() throws IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    String.format(
                        "%s/_ilm/policy/%s",
                        PROVIDER.getDatabaseUrl(),
                        TaskMigrationAdapter.LEGACY_INDEX_RETENTION_POLICY_NAME)))
            .header("Content-Type", "application/json")
            .GET()
            .build();
    final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
    final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
    final var minAge =
        jsonResponse.at(
            "/"
                + TaskMigrationAdapter.LEGACY_INDEX_RETENTION_POLICY_NAME
                + "/policy/phases/delete/min_age");
    assertThat(minAge.isMissingNode()).isFalse();
    assertThat(minAge.asText()).isEqualTo(RETENTION_AGE);
  }

  private void assertThatIsmPolicyIsPresent() throws IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    String.format(
                        "%s/_plugins/_ism/policies/%s",
                        PROVIDER.getDatabaseUrl(),
                        TaskMigrationAdapter.LEGACY_INDEX_RETENTION_POLICY_NAME)))
            .header("Content-Type", "application/json")
            .GET()
            .build();
    final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
    final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
    final var minAge = jsonResponse.at("/policy/states/0/transitions/0/conditions/min_index_age");
    assertThat(minAge.isMissingNode()).isFalse();
    assertThat(minAge.asText()).isEqualTo(RETENTION_AGE);
  }

  private List<String> getDatedTaskIndexNames() {
    try {
      final var taskIndexAlias =
          String.format("%s-tasklist-task-8.8.0_alias", PROVIDER.getIndexPrefix());
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      String.format("%s/_alias/%s", PROVIDER.getDatabaseUrl(), taskIndexAlias)))
              .header("Content-Type", "application/json")
              .GET()
              .build();
      final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
      final JsonNode jsonResponse = OBJECT_MAPPER.readTree(response.body());
      final List<String> datedIndexNames = new ArrayList<>();

      final Iterator<String> fieldNames = jsonResponse.fieldNames();
      while (fieldNames.hasNext()) {
        final var indexName = fieldNames.next();
        if (!indexName.equals(
            String.format("%s-tasklist-task-8.8.0_", PROVIDER.getIndexPrefix()))) {
          datedIndexNames.add(indexName);
        }
      }
      return datedIndexNames;
    } catch (final Exception e) {
      fail("Failed to get dated task index names", e);
      throw new RuntimeException(e);
    }
  }
}
