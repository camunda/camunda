/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.enginestate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Smoke test for the experimental mixed engine state: element instances, variables, jobs, event
 * scopes and process definitions live in PostgreSQL, the rest of the engine state in RocksDB. It
 * checks that a deployed process is stored in the database, that a process instance runs end to end
 * and its element instances, variables and job appear in and disappear from the database, that an
 * interrupting boundary event is taken through the event triggers, and that the partition log is
 * stored in the same database.
 */
@Tag("rdbms")
@TestInstance(Lifecycle.PER_CLASS)
final class RdbmsEngineStateSmokeIT {

  @AutoClose
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withStartupTimeout(Duration.ofMinutes(5));

  @AutoClose private TestStandaloneBroker broker;
  @AutoClose private CamundaClient client;

  @BeforeAll
  void beforeAll() {
    postgres.start();
    broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withSecondaryStorageType(SecondaryStorageType.none)
            .withDataConfig(
                data -> {
                  final var primaryStorage = data.getPrimaryStorage();
                  primaryStorage.setType(PrimaryStorageType.rdbms);
                  primaryStorage.getRdbms().setUrl(postgres.getJdbcUrl());
                  primaryStorage.getRdbms().setUsername(postgres.getUsername());
                  primaryStorage.getRdbms().setPassword(postgres.getPassword());
                });
    broker.start().awaitCompleteTopology();
    client = broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @Test
  void shouldStoreProcessDefinitionsElementInstancesVariablesAndJobsInPostgres()
      throws SQLException {
    // given
    final var processId = "mixed-engine-state";
    final var jobType = "mixed-engine-state-task";
    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(jobType))
                    .endEvent()
                    .done(),
                "process.bpmn")
            .send()
            .join();
    final long processDefinitionKey =
        deployment.getProcesses().getFirst().getProcessDefinitionKey();
    assertThat(countProcessDefinitionRows(processDefinitionKey, processId)).isOne();

    // when
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("orderId", "order-1"))
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("job is created")
        .untilAsserted(
            () ->
                assertThat(jobRows(processInstanceKey))
                    .containsExactly("state=0 activatable_type=" + jobType + " deadline=false"));
    final var job =
        Awaitility.await("job is activated")
            .until(
                () ->
                    client
                        .newActivateJobsCommand()
                        .jobType(jobType)
                        .maxJobsToActivate(1)
                        .send()
                        .join()
                        .getJobs(),
                jobs -> !jobs.isEmpty())
            .getFirst();

    // then
    assertThat(countRows("element_instance_key", job.getElementInstanceKey())).isOne();
    assertThat(countRows("element_instance_key", processInstanceKey)).isOne();
    assertThat(countVariableRows(processInstanceKey)).isOne();
    assertThat(job.getVariablesAsMap()).containsEntry("orderId", "order-1");
    assertThat(jobRows(processInstanceKey))
        .containsExactly("state=1 activatable_type=null deadline=true");

    // when
    client.newCompleteCommand(job).send().join();

    // then
    Awaitility.await("element instances of the completed process instance are removed")
        .untilAsserted(
            () -> assertThat(countRows("process_instance_key", processInstanceKey)).isZero());
    assertThat(countVariableRows(processInstanceKey)).isZero();
    assertThat(jobRows(processInstanceKey)).isEmpty();
    // the created and the completed job are counted under different workers: the creating one has
    // none
    Awaitility.await("the created process instance and the job are counted")
        .untilAsserted(
            () -> {
              assertThat(usageMetricCount("<default>", 1)).isPositive();
              assertThat(jobMetricRow(jobType)).isEqualTo("created=1 completed=1 failed=0");
            });

    // then the records went to the log in PostgreSQL rather than to Atomix
    assertThat(countLogBlocks()).isPositive();
  }

  private long countLogBlocks() throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement = connection.prepareStatement("SELECT COUNT(*) FROM CAM_RU_LOG_BLOCK");
        final var result = statement.executeQuery()) {
      result.next();
      return result.getLong(1);
    }
  }

  @Test
  void shouldTakeAnInterruptingBoundaryEventThroughTheEventTriggersInPostgres()
      throws SQLException {
    // given - a task with an interrupting message boundary event that leads to another task
    final var processId = "mixed-engine-state-boundary";
    final var taskJobType = "mixed-engine-state-boundary-task";
    final var cancelJobType = "mixed-engine-state-boundary-cancel";
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(taskJobType))
                .boundaryEvent(
                    "cancelled",
                    b ->
                        b.cancelActivity(true)
                            .message(
                                m -> m.name("cancel").zeebeCorrelationKeyExpression("orderId")))
                .serviceTask("handle-cancel", t -> t.zeebeJobType(cancelJobType))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done(),
            "boundary.bpmn")
        .send()
        .join();
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("orderId", "order-2"))
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("job of the task is created")
        .untilAsserted(() -> assertThat(jobRows(processInstanceKey)).hasSize(1));
    final long taskKey = jobElementInstanceKeys(processInstanceKey).getFirst();
    assertThat(eventScopeRows(processInstanceKey))
        .contains("scope=%d accepting=true interrupted=false".formatted(taskKey));

    // when - the message is correlated while the task's job is active
    client
        .newPublishMessageCommand()
        .messageName("cancel")
        .correlationKey("order-2")
        .timeToLive(Duration.ofMinutes(1))
        .variables(Map.of("reason", "customer"))
        .send()
        .join();

    // then - the boundary path is taken with the message's variables
    final var cancelJob =
        Awaitility.await("job of the boundary path is activated")
            .until(
                () ->
                    client
                        .newActivateJobsCommand()
                        .jobType(cancelJobType)
                        .maxJobsToActivate(1)
                        .send()
                        .join()
                        .getJobs(),
                jobs -> !jobs.isEmpty())
            .getFirst();
    assertThat(cancelJob.getVariablesAsMap())
        .containsEntry("reason", "customer")
        .containsEntry("orderId", "order-2");
    assertThat(countRows("element_instance_key", taskKey)).isZero();
    assertThat(eventScopeRows(processInstanceKey))
        .noneMatch(row -> row.startsWith("scope=%d ".formatted(taskKey)));
    assertThat(countTriggerRows(processInstanceKey)).isZero();

    // when
    client.newCompleteCommand(cancelJob).send().join();

    // then
    Awaitility.await("element instances of the completed process instance are removed")
        .untilAsserted(
            () -> assertThat(countRows("process_instance_key", processInstanceKey)).isZero());
    assertThat(eventScopeRows(processInstanceKey)).isEmpty();
    assertThat(countTriggerRows(processInstanceKey)).isZero();
    assertThat(jobRows(processInstanceKey)).isEmpty();
  }

  /** The event scope rows of the process instance, with their flags. */
  private List<String> eventScopeRows(final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT event_scope_key, accepting, interrupted FROM CAM_RU_EVENT_SCOPE"
                    + " WHERE process_instance_key = ? AND root_process_instance_key = ?")) {
      statement.setLong(1, processInstanceKey);
      statement.setLong(2, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        final List<String> rows = new ArrayList<>();
        while (result.next()) {
          rows.add(
              "scope=%d accepting=%s interrupted=%s"
                  .formatted(result.getLong(1), result.getBoolean(2), result.getBoolean(3)));
        }
        return rows;
      }
    }
  }

  private long countTriggerRows(final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM CAM_RU_EVENT_TRIGGER WHERE root_process_instance_key = ?")) {
      statement.setLong(1, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private List<Long> jobElementInstanceKeys(final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT element_instance_key FROM CAM_RU_JOB WHERE process_instance_key = ?")) {
      statement.setLong(1, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        final List<Long> keys = new ArrayList<>();
        while (result.next()) {
          keys.add(result.getLong(1));
        }
        return keys;
      }
    }
  }

  /** The job rows of the process instance, with their state and lookup columns. */
  private List<String> jobRows(final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT state, activatable_type, deadline FROM CAM_RU_JOB"
                    + " WHERE process_instance_key = ?")) {
      statement.setLong(1, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        final List<String> rows = new ArrayList<>();
        while (result.next()) {
          rows.add(
              "state=%d activatable_type=%s deadline=%s"
                  .formatted(result.getInt(1), result.getString(2), result.getObject(3) != null));
        }
        return rows;
      }
    }
  }

  private long usageMetricCount(final String tenantId, final int metricType) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COALESCE(SUM(metric_count), 0) FROM CAM_RU_USAGE_METRIC"
                    + " WHERE tenant_id = ? AND metric_type = ?")) {
      statement.setString(1, tenantId);
      statement.setInt(2, metricType);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private String jobMetricRow(final String jobType) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COALESCE(SUM(created_count), 0), COALESCE(SUM(completed_count), 0),"
                    + " COALESCE(SUM(failed_count), 0) FROM CAM_RU_JOB_METRIC"
                    + " WHERE job_type = ?")) {
      statement.setString(1, jobType);
      try (final var result = statement.executeQuery()) {
        result.next();
        return "created=%d completed=%d failed=%d"
            .formatted(result.getInt(1), result.getInt(2), result.getInt(3));
      }
    }
  }

  private long countVariableRows(final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM CAM_RU_VARIABLE"
                    + " WHERE process_instance_key = ? AND scope_key = ? AND name = 'orderId'")) {
      statement.setLong(1, processInstanceKey);
      statement.setLong(2, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long countProcessDefinitionRows(final long processDefinitionKey, final String processId)
      throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM CAM_RU_PROCESS_DEFINITION"
                    + " WHERE process_definition_key = ? AND bpmn_process_id = ? AND version = 1")) {
      statement.setLong(1, processDefinitionKey);
      statement.setString(2, processId);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long countRows(final String column, final long key) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM CAM_RU_ELEMENT_INSTANCE WHERE " + column + " = ?")) {
      statement.setLong(1, key);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }
}
