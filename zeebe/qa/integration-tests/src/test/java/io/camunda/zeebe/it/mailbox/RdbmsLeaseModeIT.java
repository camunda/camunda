/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A broker whose primary storage is a relational database elects its leaders by leases in that
 * database, and its log storage takes the term of the lease. It takes no snapshots, since every
 * command commits its changes to the database.
 *
 * <p>Runs on H2, so it needs no container. {@code NON_KEYWORDS=VALUE} lets H2 accept the {@code
 * value} column of the engine state's partition state table.
 */
@ZeebeIntegration
final class RdbmsLeaseModeIT {

  private static final String JDBC_URL =
      "jdbc:h2:mem:lease-mode-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";
  private static final String USER = "sa";

  // a purge leaves and rejoins the partition to drop its data, which is in the database here
  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withDataConfig(
              data -> {
                final var primaryStorage = data.getPrimaryStorage();
                primaryStorage.setType(PrimaryStorageType.rdbms);
                primaryStorage.getRdbms().setUrl(JDBC_URL);
                primaryStorage.getRdbms().setUsername(USER);
                primaryStorage.getRdbms().setPassword("");
              });

  @AutoClose private CamundaClient client;

  @BeforeEach
  void createClient() {
    client = BROKER.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @Test
  void shouldRunAProcessInstanceToTheEnd() {
    // given
    final var processId = "lease-mode-" + UUID.randomUUID();
    final var jobType = processId + "-task";
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
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .requestTimeout(Duration.ofSeconds(30))
            .send();
    final var job =
        Awaitility.await("the job is activated")
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
    client.newCompleteCommand(job).send().join();

    // then
    assertThat(processInstance.join().getProcessInstanceKey())
        .isEqualTo(job.getProcessInstanceKey());
  }

  @Test
  void shouldWriteTheLogInTheTermOfTheLease() throws SQLException {
    // when
    client.newTopologyRequest().send().join();

    // then
    final var leaseTerm =
        querySingleLong(
            "SELECT term FROM CAM_RU_PARTITION_LEASE WHERE partition_id = 1 AND released = FALSE");
    assertThat(leaseTerm).isPositive();
    assertThat(querySingleLong("SELECT TERM FROM CAM_RU_LOG_PARTITION WHERE PARTITION_ID = 1"))
        .as("the log is claimed in the term of the lease")
        .isEqualTo(leaseTerm);
    assertThat(querySingleLong("SELECT COUNT(*) FROM CAM_RU_LOG_BLOCK WHERE PARTITION_ID = 1"))
        .as("the records are written to the log in the database")
        .isPositive();
  }

  private static long querySingleLong(final String sql) throws SQLException {
    try (final var connection = DriverManager.getConnection(JDBC_URL, USER, "");
        final var statement = connection.prepareStatement(sql);
        final var result = statement.executeQuery()) {
      assertThat(result.next()).as("a row for: %s", sql).isTrue();
      return result.getLong(1);
    }
  }
}
