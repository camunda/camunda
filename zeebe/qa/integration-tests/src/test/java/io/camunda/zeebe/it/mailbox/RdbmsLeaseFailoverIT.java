/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.cluster.Zone;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Two regions with one broker each share the RDBMS primary storage. When the leader of the
 * partition stops, the broker of the other region claims the lease, takes over the log in the new
 * term, and continues the process instances of its predecessor: it rebuilds their state from the
 * log in the database, as it has none of its own.
 *
 * <p>The leader stops gracefully and releases its lease. The other region still claims it only once
 * the lease has run out, as it would after a failed region, so that a step-down keeps a partition
 * in its own region.
 *
 * <p>Runs on H2 in this JVM, so both brokers share one in-memory database. {@code
 * NON_KEYWORDS=VALUE} lets H2 accept the {@code value} column of the engine state's partition state
 * table.
 */
@ZeebeIntegration
final class RdbmsLeaseFailoverIT {

  private static final String JDBC_URL =
      "jdbc:h2:mem:lease-failover-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";
  private static final String USER = "sa";
  private static final String EAST = "failoverEast";
  private static final String WEST = "failoverWest";
  private static final Duration TAKEOVER_TIMEOUT = Duration.ofMinutes(1);

  // in lease mode the broker that does not lead is a standby, not a follower, so the topology is
  // never complete in the Raft sense; the test waits for a leader that answers instead
  @TestZeebe(awaitCompleteTopology = false, purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withName("rdbms-lease-failover")
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .multiZone(List.of(new Zone(EAST, 1, 1, 1000), new Zone(WEST, 1, 1, 500)))
          .withBrokerConfig(
              broker ->
                  broker
                      .withUnauthenticatedAccess()
                      .withSecondaryStorageType(SecondaryStorageType.none)
                      .withDataConfig(
                          data -> {
                            final var primaryStorage = data.getPrimaryStorage();
                            primaryStorage.setType(PrimaryStorageType.rdbms);
                            primaryStorage.getRdbms().setUrl(JDBC_URL);
                            primaryStorage.getRdbms().setUsername(USER);
                            primaryStorage.getRdbms().setPassword("");
                          }))
          .build();

  @Test
  void shouldContinueTheProcessInstancesInTheOtherRegionWhenTheLeaderStops() throws Exception {
    // given
    final var firstLease = awaitLease(lease -> true);
    final var leader = brokerOf(firstLease.leaderId());
    final var processId = "failover-" + UUID.randomUUID();
    final var jobType = processId + "-task";
    final long processInstanceKey;
    try (final var client = clientOf(leader)) {
      awaitLeaderAnswers(client);
      deploy(client, processId, jobType);
      processInstanceKey = createInstance(client, processId);
    }

    // when
    leader.stop();

    // then
    final var secondLease = awaitLease(lease -> lease.term() > firstLease.term());
    assertThat(secondLease.leaderId())
        .as("the broker of the other region leads")
        .isNotEqualTo(firstLease.leaderId());
    assertThat(logTerm())
        .as("the new leader writes the log in the term of its lease")
        .isEqualTo(secondLease.term());

    try (final var client = clientOf(brokerOf(secondLease.leaderId()))) {
      final var job = awaitJob(client, jobType);
      assertThat(job.getProcessInstanceKey())
          .as("the new leader continues the instance that its predecessor created")
          .isEqualTo(processInstanceKey);
      client.newCompleteCommand(job).send().join();
      assertThat(createInstance(client, processId))
          .as("the new leader generates keys after the ones of its predecessor")
          .isGreaterThan(processInstanceKey);
    }
  }

  private static TestStandaloneBroker brokerOf(final String memberId) {
    return (TestStandaloneBroker) CLUSTER.brokers().get(MemberId.from(memberId));
  }

  private static CamundaClient clientOf(final TestStandaloneBroker broker) {
    return broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  private static void awaitLeaderAnswers(final CamundaClient client) {
    Awaitility.await("the leader answers")
        .atMost(TAKEOVER_TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client.newTopologyRequest().send().join().getBrokers().stream()
                    .flatMap(broker -> broker.getPartitions().stream())
                    .anyMatch(
                        partition -> partition.isLeader() && partition.getPartitionId() == 1));
  }

  private static void deploy(
      final CamundaClient client, final String processId, final String jobType) {
    Awaitility.await("the process is deployed")
        .atMost(TAKEOVER_TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
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
                    .join(),
            deployment -> !deployment.getProcesses().isEmpty());
  }

  private static long createInstance(final CamundaClient client, final String processId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static ActivatedJob awaitJob(final CamundaClient client, final String jobType) {
    return Awaitility.await("the new leader hands out the job")
        .atMost(TAKEOVER_TIMEOUT)
        .ignoreExceptions()
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
  }

  private static Lease awaitLease(final Predicate<Lease> condition) {
    return Awaitility.await("a broker holds the lease of partition 1")
        .atMost(TAKEOVER_TIMEOUT)
        .until(RdbmsLeaseFailoverIT::heldLease, lease -> lease.filter(condition).isPresent())
        .orElseThrow();
  }

  private static Optional<Lease> heldLease() {
    try (final var connection = DriverManager.getConnection(JDBC_URL, USER, "");
        final var statement =
            connection.prepareStatement(
                "SELECT leader_id, term FROM CAM_RU_PARTITION_LEASE"
                    + " WHERE partition_id = 1 AND released = FALSE");
        final var result = statement.executeQuery()) {
      return result.next()
          ? Optional.of(new Lease(result.getString(1), result.getLong(2)))
          : Optional.empty();
    } catch (final SQLException e) {
      // the brokers create the table when they start
      return Optional.empty();
    }
  }

  private static long logTerm() throws SQLException {
    try (final var connection = DriverManager.getConnection(JDBC_URL, USER, "");
        final var statement =
            connection.prepareStatement(
                "SELECT TERM FROM CAM_RU_LOG_PARTITION WHERE PARTITION_ID = 1");
        final var result = statement.executeQuery()) {
      assertThat(result.next()).as("the log of partition 1 is claimed").isTrue();
      return result.getLong(1);
    }
  }

  private record Lease(String leaderId, long term) {}
}
