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
import io.camunda.configuration.cluster.Zone;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Two regions that are independent clusters, which share only the RDBMS primary storage. Their
 * configuration is the same except for the name of the zone: neither names the other zone, knows
 * its address, or counts its brokers. Each initialises its own cluster configuration and runs its
 * own Raft groups, so the brokers of one region never appear in the gossip of the other.
 *
 * <p>The east region starts first and takes the lease of every partition. The west region then
 * leads nothing, so every request of its gateway has to go through the mailbox in the database.
 *
 * <p>The regions share a JVM, so they are isolated by configuration, not by the network.
 */
@ZeebeIntegration
final class RdbmsIsolatedRegionsIT {

  private static final String JDBC_URL =
      "jdbc:h2:mem:isolated-regions-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";
  private static final String USER = "sa";
  private static final String EAST = "east";
  private static final String WEST = "west";
  private static final int PARTITIONS = 2;
  private static final Duration TIMEOUT = Duration.ofMinutes(1);

  // in lease mode the broker that does not lead is a standby, not a follower, so the topology is
  // never complete in the Raft sense
  @TestZeebe(awaitCompleteTopology = false, purgeAfterEach = false)
  private static final TestCluster EAST_REGION = region(EAST);

  @TestZeebe(autoStart = false, awaitCompleteTopology = false, purgeAfterEach = false)
  private static final TestCluster WEST_REGION = region(WEST);

  /** Starts west once east leads, so that west leads nothing. */
  @BeforeAll
  static void startTheWestRegionWhenTheEastRegionLeads() {
    Awaitility.await("the east region holds the lease of every partition")
        .atMost(TIMEOUT)
        .until(() -> leasesByZone().equals(Map.of(EAST, (long) PARTITIONS)));

    WEST_REGION.start();
    WEST_REGION.await(TestHealthProbe.STARTED, TIMEOUT).await(TestHealthProbe.READY, TIMEOUT);
  }

  @Test
  void shouldKnowOnlyTheBrokersOfItsOwnRegion() {
    for (final var region : List.of(EAST_REGION, WEST_REGION)) {
      try (final var client = clientOf(region)) {
        // when
        final var topology = client.newTopologyRequest().send().join();

        // then
        assertThat(topology.getClusterSize()).isOne();
        assertThat(topology.getBrokers()).hasSize(1);
      }
    }
  }

  @Test
  void shouldLeadEveryPartitionInTheRegionThatStartedFirst() {
    // when
    final var leases = leasesByZone();

    // then - the lease rows say it, and so does what each region sees
    assertThat(leases).containsExactlyEntriesOf(Map.of(EAST, (long) PARTITIONS));
    try (final var east = clientOf(EAST_REGION);
        final var west = clientOf(WEST_REGION)) {
      assertThat(leaders(east)).as("partitions led in the east region").isEqualTo(PARTITIONS);
      assertThat(leaders(west)).as("partitions led in the west region").isZero();
    }
  }

  @Test
  void shouldServeRequestsOfTheRegionWithoutLeadersThroughTheMailbox() {
    // given
    final var westBroker = brokerOf(WEST_REGION);
    final var eastBroker = brokerOf(EAST_REGION);
    final var requestsBefore =
        sum(
            westBroker,
            "zeebe_mailbox_gateway_request_latency_seconds_count",
            "outcome=\"success\"");
    final var claimedBefore = sum(eastBroker, "zeebe_mailbox_inbox_claimed_total", "");
    final var processId = "isolated-" + UUID.randomUUID();

    // when - a client of the west region deploys and starts instances on every partition
    final var processInstances = new HashMap<Integer, Long>();
    try (final var client = clientOf(WEST_REGION)) {
      deploy(client, processId, processId + "-task");
      for (int i = 0; i < 2 * PARTITIONS; i++) {
        final long key =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(processId)
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey();
        processInstances.put(i, key);
      }
    }

    // then
    assertThat(processInstances.values())
        .doesNotHaveDuplicates()
        .allSatisfy(key -> assertThat(key).isPositive());
    assertThat(
            sum(
                    westBroker,
                    "zeebe_mailbox_gateway_request_latency_seconds_count",
                    "outcome=\"success\"")
                - requestsBefore)
        .as("requests of the west gateway that were answered through the mailbox")
        .isGreaterThanOrEqualTo(1 + 2 * PARTITIONS);
    Awaitility.await("the east region took the requests out of the inbox")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(sum(eastBroker, "zeebe_mailbox_inbox_claimed_total", "") - claimedBefore)
                    .isGreaterThanOrEqualTo(1 + 2 * PARTITIONS));
  }

  @Test
  void shouldCompleteAProcessInstanceWithAWorkerOfTheRegionWithoutLeaders() {
    // given
    final var processId = "isolated-worker-" + UUID.randomUUID();
    final var jobType = processId + "-task";

    try (final var client = clientOf(WEST_REGION)) {
      deploy(client, processId, jobType);

      // when - the instance is created with a result, which waits until the job is completed
      final var result =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .withResult()
              .requestTimeout(Duration.ofSeconds(30))
              .send();
      final var job =
          Awaitility.await("the job is polled in the west region")
              .atMost(TIMEOUT)
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
      assertThat(result.join().getProcessInstanceKey()).isEqualTo(job.getProcessInstanceKey());
    }
  }

  /**
   * A region is a cluster of its own. Every region has the same configuration, but for the name of
   * its zone, which it needs as the zone of its broker and as the only zone it lists.
   */
  private static TestCluster region(final String zone) {
    return TestCluster.builder()
        .withName("isolated-regions")
        .withEmbeddedGateway(true)
        .withBrokersCount(1)
        .withPartitionsCount(PARTITIONS)
        .withReplicationFactor(1)
        .useRecordingExporter(false)
        .multiZone(List.of(new Zone(zone, 1, 1, 1)))
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
  }

  private static TestStandaloneBroker brokerOf(final TestCluster region) {
    return region.brokers().values().iterator().next();
  }

  private static CamundaClient clientOf(final TestCluster region) {
    return brokerOf(region).newClientBuilder().preferRestOverGrpc(false).build();
  }

  private static long leaders(final CamundaClient client) {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(partition -> partition.isLeader())
        .count();
  }

  private static void deploy(
      final CamundaClient client, final String processId, final String jobType) {
    Awaitility.await("the process is deployed")
        .atMost(TIMEOUT)
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

  /** The sum of the samples of a metric, whose labels contain the given text. */
  private static double sum(
      final TestStandaloneBroker broker, final String metric, final String labels) {
    return PrometheusActuator.of(broker)
        .metrics()
        .lines()
        .filter(line -> line.startsWith(metric) && line.contains(labels))
        .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
        .sum();
  }

  /** How many partitions each zone holds the lease of. */
  private static Map<String, Long> leasesByZone() {
    final Map<String, Long> leases = new HashMap<>();
    try (final var connection = DriverManager.getConnection(JDBC_URL, USER, "");
        final var statement =
            connection.prepareStatement(
                "SELECT zone, COUNT(*) FROM CAM_RU_PARTITION_LEASE WHERE released = FALSE"
                    + " GROUP BY zone");
        final var result = statement.executeQuery()) {
      while (result.next()) {
        leases.put(result.getString(1), result.getLong(2));
      }
    } catch (final SQLException e) {
      // the east region creates the table when it starts
      return Map.of();
    }
    return leases;
  }
}
