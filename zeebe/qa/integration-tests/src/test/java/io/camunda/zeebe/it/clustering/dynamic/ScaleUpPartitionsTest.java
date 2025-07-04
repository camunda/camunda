/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.DEFAULT_PROCESS_ID;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.deployProcessModel;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class ScaleUpPartitionsTest {
  private static final Logger LOG = LoggerFactory.getLogger(ScaleUpPartitionsTest.class);

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final String PROCESS_ID = DEFAULT_PROCESS_ID;
  @AutoClose CamundaClient camundaClient;

  private ClusterActuator clusterActuator;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withBrokerConfig(
              b ->
                  b.withBrokerConfig(
                      bb -> {
                        bb.getExperimental().getFeatures().setEnablePartitionScaling(true);
                        bb.getCluster()
                            .getMembership()
                            .setSyncInterval(Duration.ofSeconds(1))
                            .setGossipInterval(Duration.ofSeconds(1));
                      }))
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldDeployProcessesToNewPartitionsAndStartNewInstances() {
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    cluster.awaitHealthyTopology();
    // when
    scaleToPartitions(desiredPartitionCount);

    awaitScaleUpCompletion(desiredPartitionCount);
    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, desiredPartitionCount);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  public void shouldStartProcessInstancesDeployedBeforeScaleUp(final int partitionsToAdd) {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + partitionsToAdd;
    cluster.awaitHealthyTopology();

    // when
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);

    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    for (int i = 0; i < 20; i++) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, PROCESS_ID);
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldStartProcessInstancesDeployedWhenScalingUp() {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + 3;
    cluster.awaitHealthyTopology();

    deployProcessModel(camundaClient, JOB_TYPE, "baseProcess");
    // when
    scaleToPartitions(desiredPartitionCount);

    final var processIds = new ArrayList<String>();
    for (int i = 0; i < 10; i++) {
      final var id = "processId-" + i;
      processIds.add(id);
      // do not wait for the deployment to be distributed to all partitions
      final var deploymentKey = deployProcessModel(camundaClient, JOB_TYPE, id, false);
      LOG.debug("Deployed process model with id: {}, key: {}", id, deploymentKey);
    }

    awaitScaleUpCompletion(desiredPartitionCount);

    // then
    for (final var processId : processIds) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, processId);
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldScaleUpMultipleTimes() {
    // given
    final var firstScaleUp = PARTITIONS_COUNT + 1;
    final var secondScaleUp = firstScaleUp + 1;

    cluster.awaitHealthyTopology();

    // Scale up to first partition count
    scaleToPartitions(firstScaleUp);
    awaitScaleUpCompletion(firstScaleUp);

    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, firstScaleUp, true, PROCESS_ID);

    // when
    // Scale up to second partition count
    scaleToPartitions(secondScaleUp);
    awaitScaleUpCompletion(secondScaleUp);

    // then
    createInstanceWithAJobOnAllPartitions(
        camundaClient, JOB_TYPE, secondScaleUp, false, PROCESS_ID);
    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldDeleteBootstrapSnapshotWhenScalingIsDone() {
    cluster.awaitHealthyTopology();
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);
    final var targetPartitionCount = PARTITIONS_COUNT + 1;

    final var partition1Leader = cluster.leaderForPartition(1);

    final var directory = Path.of(partition1Leader.brokerConfig().getData().getDirectory());
    final var bootstrapSnapshotDirectory =
        directory.resolve("raft-partition/partitions/1/bootstrap-snapshots/1-1-0-0-0");
    scaleToPartitions(targetPartitionCount);
    Awaitility.await("until snapshot is created")
        // to limit flakyness, the folder is checked every millisecond
        .pollInterval(Duration.ofMillis(1))
        .untilAsserted(
            () -> {
              assertThat(bootstrapSnapshotDirectory).exists();
            });
    awaitScaleUpCompletion(targetPartitionCount);

    Awaitility.await("until snapshot is created")
        // to limit flakyness, the folder is checked every millisecond
        .pollInterval(Duration.ofMillis(1))
        .untilAsserted(
            () -> {
              assertThat(bootstrapSnapshotDirectory).doesNotExist();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"partition1Leader", "bootstrapNode"})
  public void shouldSucceedScaleUpWhenCriticalNodesRestart(final String restartTarget) {
    // given - healthy cluster
    cluster.awaitCompleteTopology();
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);
    final var targetPartitionCount = PARTITIONS_COUNT + 1;

    final var member0 = MemberId.from("0");
    if ("bootstrapNode".equals(restartTarget)
        && cluster.leaderForPartition(1).nodeId().equals(member0)) {
      // if we need to restart the bootstrap node, make sure that it's not the same as the
      // leader for partition 1`
      LOG.info(
          "Restarting node {} because it's the leader for partition 1 and the target node for bootstrapping ",
          member0);
      cluster.leaderForPartition(1).stop().start();
      cluster.awaitCompleteTopology();
    }
    // when - start scaling up
    scaleToPartitions(targetPartitionCount);

    // Restart the appropriate node based on the test argument
    final var brokerToRestart =
        switch (restartTarget) {
          case "partition1Leader" -> cluster.leaderForPartition(1);
          case "bootstrapNode" -> cluster.brokers().get(member0);
          default -> throw new IllegalArgumentException("Unknown restart target: " + restartTarget);
        };

    LOG.info("Restarting node {} ", brokerToRestart.nodeId());
    brokerToRestart.stop().start();
    LOG.info("Restarted node {} ", brokerToRestart.nodeId());
    Awaitility.await("restarted broker is ready")
        .until(
            () -> {
              try {
                brokerToRestart.healthActuator().ready();
                return true;
              } catch (final Exception e) {
                return false;
              }
            });

    // then - scale up should still complete successfully
    awaitScaleUpCompletion(targetPartitionCount);

    // Verify the new partition is functional
    createInstanceWithAJobOnAllPartitions(
        camundaClient, JOB_TYPE, targetPartitionCount, false, PROCESS_ID);
    cluster.awaitHealthyTopology();
  }

  private PlannedOperationsResponse scaleToPartitions(final int desiredPartitionCount) {
    return clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(desiredPartitionCount)
                    .replicationFactor(3)),
        false,
        false);
  }

  private void awaitScaleUpCompletion(final int desiredPartitionCount) {
    Awaitility.await("until scaling is done")
        .atMost(Duration.ofMinutes(2))
        .catchUncaughtExceptions()
        .logging()
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              try {
                final var topology = clusterActuator.getTopology();
                assertThat(topology.getRouting()).isNotNull();
                final var requestHandling = topology.getRouting().getRequestHandling();
                assertThat(requestHandling).isInstanceOf(RequestHandlingAllPartitions.class);
                final var allPartitions = (RequestHandlingAllPartitions) requestHandling;
                assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
              } catch (final Exception e) {
                System.err.println("Got exception in assertion");
                e.printStackTrace();
                throw new RuntimeException(e);
              }
            });
  }
}
