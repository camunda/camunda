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
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The experimental mixed engine state keeps its relational rows across a broker restart: the replay
 * rebuilds RocksDB from the log but skips the changes the database already contains, and a process
 * instance started before the restart completes after it.
 */
@Tag("rdbms")
@TestInstance(Lifecycle.PER_CLASS)
final class RdbmsEngineStateRestartIT {

  @TempDir private static Path workingDirectory;

  @AutoClose
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withStartupTimeout(Duration.ofMinutes(5));

  private TestStandaloneBroker broker;
  private CamundaClient client;

  @BeforeAll
  void beforeAll() {
    postgres.start();
    broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withWorkingDirectory(workingDirectory)
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
    client = newClient();
  }

  @AfterAll
  void afterAll() {
    client.close();
    broker.close();
  }

  @Test
  void shouldCompleteAProcessInstanceStartedBeforeTheRestart() throws SQLException {
    // given - a process instance waiting at a service task
    final var processId = "mixed-engine-state-restart";
    final var jobType = "mixed-engine-state-restart-task";
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
        .untilAsserted(() -> assertThat(countRows("CAM_RU_JOB", processInstanceKey)).isOne());
    final long elementInstanceRows = countRows("CAM_RU_ELEMENT_INSTANCE", processInstanceKey);
    assertThat(elementInstanceRows).isEqualTo(2);
    assertThat(lastProcessedPosition()).isPositive();

    // when
    client.close();
    broker.stop();
    broker.start().awaitCompleteTopology();
    client = newClient();

    // then - the rows are kept
    assertThat(countRows("CAM_RU_ELEMENT_INSTANCE", processInstanceKey))
        .isEqualTo(elementInstanceRows);
    assertThat(countRows("CAM_RU_VARIABLE", processInstanceKey)).isOne();
    assertThat(countRows("CAM_RU_JOB", processInstanceKey)).isOne();

    // when - the job is activated and completed after the restart
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
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getVariablesAsMap()).containsEntry("orderId", "order-1");
    client.newCompleteCommand(job).send().join();

    // then - the process instance completes
    Awaitility.await("rows of the completed process instance are removed")
        .untilAsserted(
            () -> assertThat(countRows("CAM_RU_ELEMENT_INSTANCE", processInstanceKey)).isZero());
    assertThat(countRows("CAM_RU_VARIABLE", processInstanceKey)).isZero();
    assertThat(countRows("CAM_RU_JOB", processInstanceKey)).isZero();
  }

  private CamundaClient newClient() {
    return broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  private long countRows(final String table, final long processInstanceKey) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE process_instance_key = ?")) {
      statement.setLong(1, processInstanceKey);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long lastProcessedPosition() throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery(
                "SELECT value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = 1 AND state_key = 'last-processed-position'")) {
      result.next();
      return Long.parseLong(result.getString(1));
    }
  }
}
