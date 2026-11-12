/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.cluster.messaging.impl.NettyUnicastService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

/**
 * Three brokers that keep the state of their one partition in a relational database. Only the
 * leader writes the state, and a broker that becomes the leader takes the state over with its own
 * term. The followers keep no state, but take snapshots of the shared state so they can compact
 * their logs. Subclasses choose the database.
 */
abstract class ReplicatedRdbmsPartitionStateTestBase {

  private static final int PARTITION_ID = 1;

  /** More log entries than raft keeps behind a snapshot, filling about three segments of 1 MB. */
  private static final int FILLER_MESSAGES = 200;

  private static final String FILLER_PAYLOAD = "x".repeat(16 * 1024);

  @TempDir private Path workingDirectory;
  private final String jobType = "task-" + UUID.randomUUID();
  private StateDatabase database;

  /** Creates an empty database for one test. */
  abstract StateDatabase createDatabase();

  /** Removes the database of a test, once its brokers are stopped. */
  void dropDatabase(final StateDatabase database) {}

  @BeforeEach
  final void setUpDatabase() {
    database = createDatabase();
  }

  @AfterEach
  final void tearDownDatabase() {
    dropDatabase(database);
  }

  @Test
  void shouldTakeOverTheStateWhenTheLeaderStops() {
    try (final var cluster = startCluster()) {
      // given
      final var oldLeader = cluster.leaderForPartition(PARTITION_ID);
      final List<Long> processInstanceKeys = new ArrayList<>();
      try (final var client = oldLeader.newClientBuilder().build()) {
        deployProcess(client);
        processInstanceKeys.addAll(createProcessInstances(client, 10));
      }

      // when
      oldLeader.stop();
      final var newLeader = awaitLeaderAmong(otherBrokers(cluster, oldLeader));

      // then
      try (final var client = newLeader.newClientBuilder().build()) {
        processInstanceKeys.addAll(createProcessInstances(client, 5));
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
      assertThatStateIsOwnedBy(newLeader);
    }
  }

  @Test
  void shouldLeadWithACompactedLogAfterARestart() {
    try (final var cluster = startCluster()) {
      // given - followers that compacted their logs, so that they no longer have the instances
      // that only the database has
      final var leader = cluster.leaderForPartition(PARTITION_ID);
      final var followers = otherBrokers(cluster, leader);
      final List<Long> processInstanceKeys = new ArrayList<>();
      try (final var client = leader.newClientBuilder().build()) {
        deployProcess(client);
        processInstanceKeys.addAll(createProcessInstances(client, 10));
        publishFillerMessages(client);
      }
      final var segmentsBeforeSnapshot =
          followers.stream().collect(Collectors.toMap(follower -> follower, this::segments));
      // the first snapshot comes one snapshot period after a broker becomes a follower
      Awaitility.await("The followers delete the segments their snapshots replace")
          .atMost(Duration.ofMinutes(3))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () ->
                  assertThat(followers)
                      .allSatisfy(
                          follower ->
                              assertThat(
                                      segments(follower)
                                          .containsAll(segmentsBeforeSnapshot.get(follower)))
                                  .describedAs(
                                      "broker %s still has all its old segments",
                                      follower.nodeId().id())
                                  .isFalse()));
      final var follower = followers.getFirst();
      follower.stop();
      follower.start();
      cluster.awaitCompleteTopology();
      try (final var client = leader.newClientBuilder().build()) {
        processInstanceKeys.addAll(createProcessInstances(client, 5));
      }

      // when
      makeLeader(follower);

      // then
      try (final var client = follower.newClientBuilder().build()) {
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
      assertThatStateIsOwnedBy(follower);
    }
  }

  @Test
  void shouldTakeTheStateBackAfterTheLeaderWasCutOff() {
    try (final var cluster = startCluster()) {
      // given - a leader that can't reach the others while they choose a new leader
      final var oldLeader = cluster.leaderForPartition(PARTITION_ID);
      final List<Long> processInstanceKeys = new ArrayList<>();
      try (final var client = oldLeader.newClientBuilder().build()) {
        deployProcess(client);
        processInstanceKeys.addAll(createProcessInstances(client, 10));
      }
      disconnect(oldLeader);
      final var newLeader = awaitLeaderAmong(otherBrokers(cluster, oldLeader));
      try (final var client = newLeader.newClientBuilder().build()) {
        processInstanceKeys.addAll(createProcessInstances(client, 5));
      }
      connect(oldLeader);

      // when
      makeLeader(oldLeader);

      // then
      try (final var client = oldLeader.newClientBuilder().build()) {
        processInstanceKeys.addAll(createProcessInstances(client, 5));
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
      assertThatStateIsOwnedBy(oldLeader);
    }
  }

  private TestCluster startCluster() {
    return TestCluster.builder()
        .withBrokersCount(3)
        .withPartitionsCount(1)
        .withReplicationFactor(3)
        .withEmbeddedGateway(true)
        .withBrokerConfig(
            (memberId, broker) ->
                broker
                    .withUnauthenticatedAccess()
                    .withWorkingDirectory(workingDirectory.resolve(memberId.id()))
                    .withUnifiedConfig(
                        config -> {
                          final var primaryStorage = config.getData().getPrimaryStorage();
                          primaryStorage.setType(PrimaryStorageType.rdbms);
                          primaryStorage.getRdbms().setUrl(database.url());
                          primaryStorage.getRdbms().setUsername(database.username());
                          primaryStorage.getRdbms().setPassword(database.password());
                          primaryStorage.getWriteBehindCache().setEnabled(true);
                          // small segments, so that a snapshot lets raft delete the oldest ones
                          primaryStorage.getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
                          config
                              .getCluster()
                              .getNetwork()
                              .setMaxMessageSize(DataSize.ofKilobytes(500));
                          config.getData().setSnapshotPeriod(Duration.ofMinutes(1));
                        }))
        .build()
        .start()
        .awaitCompleteTopology();
  }

  private void assertThatStateIsOwnedBy(final TestStandaloneBroker leader) {
    assertThat(ownerTermOfPartitionOne())
        .describedAs("the leader claims the state with its own term")
        .isEqualTo(raftPartition(leader).term());
  }

  private long ownerTermOfPartitionOne() {
    try (final var connection =
            DriverManager.getConnection(database.url(), database.username(), database.password());
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery(
                "SELECT OWNER_TERM FROM ZEEBE_STATE_PARTITION WHERE PARTITION_ID = "
                    + PARTITION_ID)) {
      assertThat(result.next()).describedAs("a row for partition %d", PARTITION_ID).isTrue();
      return result.getLong(1);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to read the owner term of the partition", e);
    }
  }

  private Set<Path> segments(final TestStandaloneBroker broker) {
    final var logDirectory =
        workingDirectory
            .resolve(broker.nodeId().id())
            .resolve("data/default/partitions/" + PARTITION_ID);
    try (final Stream<Path> files = Files.list(logDirectory)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".log"))
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();
  }

  private static List<Long> createProcessInstances(final CamundaClient client, final int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId("process")
                    .latestVersion()
                    .send()
                    .join()
                    .getProcessInstanceKey())
        .toList();
  }

  private static void publishFillerMessages(final CamundaClient client) {
    for (int i = 0; i < FILLER_MESSAGES; i++) {
      client
          .newPublishMessageCommand()
          .messageName("filler")
          .correlationKey("filler-" + i)
          .timeToLive(Duration.ZERO)
          .variables(Map.of("payload", FILLER_PAYLOAD))
          .send()
          .join();
    }
  }

  private static List<TestStandaloneBroker> otherBrokers(
      final TestCluster cluster, final TestStandaloneBroker broker) {
    return cluster.brokers().values().stream().filter(other -> other != broker).toList();
  }

  /** Waits until one of the candidates leads the partition and its gateway knows it. */
  private static TestStandaloneBroker awaitLeaderAmong(
      final Collection<TestStandaloneBroker> candidates) {
    final var leader =
        Awaitility.await("One of the other brokers becomes the leader")
            .atMost(Duration.ofMinutes(1))
            .until(
                () ->
                    candidates.stream()
                        .filter(broker -> raftPartition(broker).getRole() == Role.LEADER)
                        .findFirst(),
                Optional::isPresent)
            .orElseThrow();
    awaitGatewaySeesLeader(leader);
    return leader;
  }

  /** Makes the broker the leader, retrying while it is still catching up with the leader. */
  private static void makeLeader(final TestStandaloneBroker broker) {
    Awaitility.await("Broker %s becomes the leader".formatted(broker.nodeId().id()))
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(raftPartition(broker).getServer().anoint())
                  .succeedsWithin(Duration.ofSeconds(15));
              assertThat(raftPartition(broker).getRole()).isEqualTo(Role.LEADER);
            });
    awaitGatewaySeesLeader(broker);
  }

  private static void awaitGatewaySeesLeader(final TestStandaloneBroker leader) {
    final int leaderId = Integer.parseInt(leader.nodeId().id());
    try (final var client = leader.newClientBuilder().build()) {
      Awaitility.await("The gateway of broker %d knows it is the leader".formatted(leaderId))
          .atMost(Duration.ofMinutes(1))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .hasLeaderForPartition(PARTITION_ID, leaderId));
    }
  }

  /** Cuts the broker off from the other brokers, like a network partition would. */
  private static void disconnect(final TestStandaloneBroker broker) {
    // unlike ClusteringRule#disconnect, keeps the command API up, so the broker can take commands
    // again once it is the leader
    final var cluster = broker.bean(Broker.class).getSystemContext().getCluster();
    ((NettyUnicastService) cluster.getUnicastService()).stop().join();
    ((NettyMessagingService) cluster.getMessagingService()).stop().join();
  }

  private static void connect(final TestStandaloneBroker broker) {
    final var cluster = broker.bean(Broker.class).getSystemContext().getCluster();
    ((NettyUnicastService) cluster.getUnicastService()).start().join();
    ((NettyMessagingService) cluster.getMessagingService()).start().join();
  }

  private static RaftPartition raftPartition(final TestStandaloneBroker broker) {
    final var partitionManager =
        broker
            .bean(Broker.class)
            .getBrokerContext()
            .getPartitionManagers()
            .get(DEFAULT_PHYSICAL_TENANT_ID);
    return Objects.requireNonNull(partitionManager.getRaftPartition(PARTITION_ID));
  }

  /** Where the brokers keep the partition's state, and how they log in. */
  record StateDatabase(String url, String username, String password) {}
}
