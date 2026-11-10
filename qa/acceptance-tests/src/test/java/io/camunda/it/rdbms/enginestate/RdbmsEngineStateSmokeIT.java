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
 * Smoke test for the experimental mixed engine state: element instances, variables and process
 * definitions live in PostgreSQL, the rest of the engine state in RocksDB. It checks that a
 * deployed process is stored in the database, and that a process instance runs end to end and its
 * element instances and variables appear in and disappear from the database.
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
  void shouldStoreProcessDefinitionsElementInstancesAndVariablesInPostgres() throws SQLException {
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

    // when
    client.newCompleteCommand(job).send().join();

    // then
    Awaitility.await("element instances of the completed process instance are removed")
        .untilAsserted(
            () -> assertThat(countRows("process_instance_key", processInstanceKey)).isZero());
    assertThat(countVariableRows(processInstanceKey)).isZero();
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
