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
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Smoke test for the experimental mixed engine state: element instances live in PostgreSQL, the
 * rest of the engine state in RocksDB. It checks that a process instance runs end to end and that
 * its element instances appear in and disappear from the database.
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
            .withProperty("zeebe.broker.experimental.rdbmsEngineState.enabled", true)
            .withProperty("zeebe.broker.experimental.rdbmsEngineState.url", postgres.getJdbcUrl())
            .withProperty(
                "zeebe.broker.experimental.rdbmsEngineState.username", postgres.getUsername())
            .withProperty(
                "zeebe.broker.experimental.rdbmsEngineState.password", postgres.getPassword());
    broker.start().awaitCompleteTopology();
    client = broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @Test
  void shouldStoreElementInstancesInPostgres() throws SQLException {
    // given
    final var processId = "mixed-engine-state";
    final var jobType = "mixed-engine-state-task";
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

    // when
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
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

    // when
    client.newCompleteCommand(job).send().join();

    // then
    Awaitility.await("element instances of the completed process instance are removed")
        .untilAsserted(
            () -> assertThat(countRows("process_instance_key", processInstanceKey)).isZero());
  }

  private long countRows(final String column, final long key) throws SQLException {
    try (final var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM element_instance WHERE " + column + " = ?")) {
      statement.setLong(1, key);
      try (final var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }
}
