/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static org.assertj.core.api.Assertions.*;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
abstract class ClusterEndpointIT {

  protected abstract int brokerCount();

  protected abstract int partitionCount();

  protected abstract TestCluster createCluster(
      int brokerCount, int partitionCount, int replicationFactor);

  protected TestCluster createCluster(final int replicationFactor) {
    return createCluster(brokerCount(), partitionCount(), replicationFactor);
  }

  /**
   * The minimum valid replication factor for this cluster type. Non-zone-aware clusters can use
   * RF=1; zone-aware clusters require at least 1 replica per zone, so RF>=2.
   */
  protected int minReplicationFactor() {
    return 1;
  }

  protected abstract String zone();

  protected abstract BrokerId brokerId(int nodeIdx);

  /**
   * Returns the internal {@link MemberId} (TestCluster key) for a given node index. Subclasses
   * override this when the TestCluster internal IDs differ from the API-level node indices.
   */
  protected MemberId memberIdForBroker(final int nodeIdx) {
    return MemberId.from(java.lang.String.valueOf(nodeIdx));
  }

  protected List<BrokerId> brokerIds(final int... nodeIdxs) {
    return IntStream.of(nodeIdxs).mapToObj(this::brokerId).toList();
  }

  protected static String scaleRequestBody(final TestCluster cluster) {
    return cluster.brokers().keySet().stream().toList().toString();
  }

  @Test
  void shouldFailRequestWhenHavingTypoInParameter() throws IOException, InterruptedException {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();

      // when
      final var response = sendBrokerScaleRequest(cluster, URI.create("/brokers?dry-run=true"));

      // then
      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(response.body()).contains("Unsupported query parameter(s): dry-run");
    }
  }

  private void movePartition(final ClusterActuator actuator, final BrokerId id, final int rf) {
    final var topology = actuator.getTopology();
    final var brokerState =
        topology.getBrokers().stream().filter(b -> b.getId().equals(id)).findFirst().orElseThrow();
    final var targetBroker =
        topology.getBrokers().stream()
            .filter(b -> !b.getId().equals(id))
            .findFirst()
            .orElseThrow()
            .getId();

    for (final var partition : brokerState.getPartitions()) {
      // First join another broker so the partition keeps at least one replica
      if (rf == 1) {
        final var join = actuator.joinPartition(targetBroker, partition.getId(), 1);
        Awaitility.await()
            .untilAsserted(
                () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(join));
      }

      final var leave = actuator.leavePartition(id, partition.getId());
      Awaitility.await()
          .untilAsserted(() -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(leave));
    }
  }

  private static java.net.http.HttpResponse<String> sendBrokerScaleRequest(
      final TestCluster cluster, final URI pathAndQuery) throws IOException, InterruptedException {
    final var baseClusterEndpoint = cluster.availableGateway().actuatorUri("cluster");
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseClusterEndpoint + pathAndQuery.toString()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(scaleRequestBody(cluster)))
            .build();

    try (final var httpClient = HttpClient.newHttpClient()) {
      return httpClient.send(request, BodyHandlers.ofString());
    }
  }

  @Test
  void shouldQueryCurrentClusterTopology() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var response = actuator.getTopology();

      // then - topology is as expected
      assertThat(response.getBrokers())
          .hasSize(brokerCount())
          .allSatisfy(
              b ->
                  assertThat(b.getPartitions())
                      .describedAs("All brokers have all partitions")
                      .hasSize(partitionCount()));
      assertThat(response.getLastChange()).isNull();
      assertThat(response.getPendingChange()).isNull();
    }
  }

  @Test
  void shouldRequestPartitionLeave() {
    try (final var cluster = createCluster(minReplicationFactor() + 1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- request a leave
      final var response = actuator.leavePartition(brokerId(1), 2);

      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_LEAVE, Operation::getOperation)
          .returns(brokerId(1), Operation::getBrokerId)
          .returns(2, Operation::getPartitionId);
    }
  }

  @Test
  void shouldRequestClusterPurge() {
    try (final var cluster = createCluster(minReplicationFactor() + 1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- request a purge
      final var response = actuator.purge(false);

      // then
      final int replicationFactor = minReplicationFactor() + 1;
      final var expected = new ArrayList<OperationEnum>();
      IntStream.range(0, partitionCount() * replicationFactor)
          .forEach(i -> expected.add(OperationEnum.PARTITION_LEAVE));
      expected.addAll(
          List.of(OperationEnum.DELETE_HISTORY, OperationEnum.UPDATE_INCARNATION_NUMBER));
      IntStream.range(0, partitionCount())
          .forEach(i -> expected.add(OperationEnum.PARTITION_BOOTSTRAP));
      expected.addAll(
          IntStream.range(0, partitionCount() * (replicationFactor - 1))
              .mapToObj(i -> OperationEnum.PARTITION_JOIN)
              .toList());
      assertThat(response.getPlannedChanges().stream().map(Operation::getOperation))
          .containsExactlyElementsOf(expected);
    }
  }

  @Test
  void shouldRequestPartitionJoin() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- request a join
      final var response = actuator.joinPartition(brokerId(0), 2, 3, true);

      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_JOIN, Operation::getOperation)
          .returns(brokerId(0), Operation::getBrokerId)
          .returns(2, Operation::getPartitionId)
          .returns(3, Operation::getPriority);
    }
  }

  @Test
  void shouldRejectJoinOnNonExistingPartition() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- request a join with a non-existing partition
      // Use bare integer brokerId so the request reaches the server on zone-aware clusters too.
      // Non-zone-aware rejects with "partition has no active members";
      // zone-aware rejects with "zone-aware" (join not supported).
      assertThatCode(() -> actuator.joinPartition(0, partitionCount() + 1, 3))
          .describedAs("Joining should fail with 400 Bad Request")
          .isInstanceOf(FeignException.BadRequest.class);
    }
  }

  @Test
  void shouldRequestScaleBrokers() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- scale up by one broker
      final var scaleIds =
          IntStream.rangeClosed(0, brokerCount()).mapToObj(this::brokerId).toList();
      final var response = actuator.scaleByBrokerIds(scaleIds, true);

      // then
      assertThat(response.getExpectedTopology()).hasSize(brokerCount() + 1);
    }
  }

  @Test
  void shouldRequestForceScaleDownBrokers() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      cluster.brokers().get(MemberId.from("1")).close();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - force remove broker 1
      final var response = actuator.scaleByBrokerIds(brokerIds(0), false, true);

      // then
      assertThat(response.getExpectedTopology()).hasSize(1);
    }
  }

  @Test
  void shouldRequestAddBroker() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- add a new broker beyond the current set
      final var response = actuator.addBroker(brokerId(brokerCount()), true);

      // then
      assertThat(response.getExpectedTopology()).hasSize(brokerCount() + 1);
    }
  }

  @Test
  void shouldRequestRemoveBroker() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // Must move partitions off the broker before removing it from the cluster
      final var id = brokerId(brokerCount() - 1);
      movePartition(actuator, id, minReplicationFactor());

      // when
      final var response = actuator.removeBroker(id);

      // then
      assertThat(response.getExpectedTopology()).hasSize(brokerCount() - 1);
    }
  }

  @Test
  void canDryRunScale() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      final var initialTopology = actuator.getTopology();

      // when -- dry-run scale: keep all but broker 0, add one new broker
      final var dryRunIds =
          IntStream.rangeClosed(1, brokerCount()).mapToObj(this::brokerId).toList();
      final var dryRun = actuator.scaleByBrokerIds(dryRunIds, true);

      // then -- dry run response looks as expected
      assertThat(dryRun.getExpectedTopology()).hasSize(brokerCount());
      assertThat(dryRun.getPlannedChanges()).isNotEmpty();
      assertThat(dryRun.getCurrentTopology()).isEqualTo(initialTopology.getBrokers());
      // then -- topology did not change
      assertThat(actuator.getTopology()).isEqualTo(initialTopology);
    }
  }

  @Nested
  final class ClusterPatchRequest {
    @Test
    void shouldRequestClusterScale() {
      try (final var cluster = createCluster(minReplicationFactor())) {
        // given
        cluster.awaitCompleteTopology();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().count(brokerCount()))
                .partitions(
                    new ClusterConfigPatchRequestPartitions()
                        .count(partitionCount())
                        .replicationFactor(minReplicationFactor() + 1));
        final var response = actuator.patchCluster(request, true, false);
        // then
        assertThat(response.getExpectedTopology())
            .describedAs("ClusterSize is " + brokerCount())
            .hasSize(brokerCount());
        assertThat(response.getExpectedTopology().getFirst().getPartitions().size())
            .describedAs("Partitions are evenly distributed")
            .isEqualTo(response.getExpectedTopology().getLast().getPartitions().size());

        assertThat(response.getPlannedChanges()).isNotEmpty();
      }
    }

    @Test
    void shouldRequestClusterPatch() {
      try (final var cluster = createCluster(minReplicationFactor())) {
        // given
        cluster.awaitCompleteTopology();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().add(List.of(brokerId(1))))
                .partitions(
                    new ClusterConfigPatchRequestPartitions()
                        .count(partitionCount())
                        .replicationFactor(minReplicationFactor() + 1));
        final var response = actuator.patchCluster(request, true, false);
        // then
        assertThat(response.getExpectedTopology())
            .describedAs("Cluster has " + brokerCount() + " brokers")
            .hasSize(brokerCount());
        assertThat(response.getExpectedTopology().getFirst().getPartitions().size())
            .describedAs("Partitions are evenly distributed")
            .isEqualTo(response.getExpectedTopology().getLast().getPartitions().size());

        assertThat(response.getPlannedChanges()).isNotEmpty();
      }
    }

    @Test
    void shouldRequestForceRemoveBroker() {
      try (final var cluster = createCluster(minReplicationFactor() + 1)) {
        // given
        cluster.awaitCompleteTopology();
        cluster.brokers().get(memberIdForBroker(1)).close();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when - force remove broker 1
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().remove(List.of(brokerId(1))));
        final var response = actuator.patchCluster(request, false, true);

        // then
        assertThat(response.getExpectedTopology()).hasSize(brokerCount() - 1);
      }
    }
  }
}
