/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.DEFAULT_PROCESS_ID;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceOnAllPartitions;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.deployProcessModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.management.cluster.RoutingState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractStartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class ScaleUpPartitionsTest {
  private static final Logger LOG = LoggerFactory.getLogger(ScaleUpPartitionsTest.class);

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final String PROCESS_ID = DEFAULT_PROCESS_ID;
  // must be static so that it's initialized before the cluster
  @TempDir private static Path backupPath;

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  private static final MemberId MEMBER_0 = MemberId.from("0");
  @AutoClose CamundaClient camundaClient;
  private ClusterActuator clusterActuator;
  private BackupActuator backupActuator;

  private final String containerName =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();

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
                  b.withBrokerConfig(this::configureBackupStore)
                      .withBrokerConfig(
                          bb -> {
                            bb.getExperimental().getFeatures().setEnablePartitionScaling(true);
                            bb.getCluster()
                                .getMembership()
                                .setSyncInterval(Duration.ofSeconds(1))
                                .setGossipInterval(Duration.ofMillis(500));
                          }))
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
    backupActuator = BackupActuator.of(cluster.availableGateway());
  }

  private void configureBackupStore(final LegacyBrokerBasedProperties bb) {
    final var backup = bb.getData().getBackup();
    backup.setStore(BackupStoreType.AZURE);
    final var azure = backup.getAzure();

    backup.setStore(BackupStoreType.AZURE);
    azure.setBasePath(containerName);
    azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
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
  @EnumSource(RestartTarget.class)
  public void shouldSucceedScaleUpWhenCriticalNodesRestart(final RestartTarget restartTarget) {
    // given - healthy cluster
    cluster.awaitCompleteTopology();
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);
    final var targetPartitionCount = PARTITIONS_COUNT + 1;

    final var member0 = MemberId.from("0");
    if (RestartTarget.BOOTSTRAP_NODE == restartTarget
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
    final var brokerToRestart = restartTarget.restart(cluster);

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldBePossibleToRestoreAsAfterScalingUp(final boolean backupBeforeScaling)
      throws IOException {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    cluster.awaitHealthyTopology();
    final var backupId = 1L;

    // when
    if (backupBeforeScaling) {
      backupActuator.take(backupId);
      assertBackupIsCompleted(backupId);
    }
    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    final var routingStateAfterScaling = clusterActuator.getTopology().getRouting();

    if (!backupBeforeScaling) {
      backupActuator.take(backupId);
      assertBackupIsCompleted(backupId);
    }

    cluster.close();
    LOG.info("Cluster stopped, restoring all brokers");

    restoreAllBrokers(backupId);

    LOG.info("All brokers restored, starting cluster");
    // then
    // the topology is restored to the desired partition count and message correlation
    cluster.start();
    cluster.awaitCompleteTopology();
    if (backupBeforeScaling) {
      assertThatRoutingStateMatches(
          new RoutingState()
              .requestHandling(new RequestHandlingAllPartitions(3).strategy("AllPartitions"))
              .messageCorrelation(new MessageCorrelationHashMod("HashMod", 3)));
    } else {
      assertThatRoutingStateMatches(routingStateAfterScaling);
    }
  }

  @Test
  public void shouldNotBreakMessageCorrelation() {
    // given
    cluster.awaitHealthyTopology();
    final var correlationKeyVariable = "correlationKey";
    final var processId = "PROCESS_WITH_MESSAGE";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent(
                "catch-me",
                b ->
                    b.message(
                        m ->
                            m.name("message")
                                .zeebeCorrelationKeyExpression(correlationKeyVariable)))
            .endEvent()
            .done();

    final var deploymentKey =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(camundaClient).waitUntilDeploymentIsDone(deploymentKey);
    final var targetPartitionCount = PARTITIONS_COUNT + 1;

    final var variableProvider = new CorrelationKeyVariableProvider();

    createInstanceOnAllPartitions(
        camundaClient,
        PARTITIONS_COUNT,
        processId,
        variableProvider.supplier(correlationKeyVariable));

    // when
    scaleToPartitions(targetPartitionCount);
    awaitScaleUpCompletion(targetPartitionCount);

    createInstanceOnAllPartitions(
        camundaClient,
        targetPartitionCount,
        processId,
        variableProvider.supplier(correlationKeyVariable));

    // then
    // After creating instances, wait for subscriptions to be established
    variableProvider.correlateAllMessages(camundaClient, "message");
  }

  @ParameterizedTest
  @EnumSource(value = TestCase.class)
  public void shouldNotBreakProcessWithStartEvents(final TestCase testCase) {
    // given
    cluster.awaitHealthyTopology();
    final var correlationKeyVariable = "correlationKey";
    final var processId = "PROCESS_WITH_MESSAGE_START";
    final var continueMessageName = "continueMessage";
    // Create process with message start event and intermediate message event
    final var process =
        testCase
            .setupStartEvent(Bpmn.createExecutableProcess(processId))
            .intermediateCatchEvent(
                "intermediate-message",
                b ->
                    b.message(
                        m ->
                            m.name(continueMessageName)
                                .zeebeCorrelationKeyExpression(correlationKeyVariable)))
            .endEvent()
            .done();

    final var deploymentKey =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(process, "process-with-message-start.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(camundaClient).waitUntilDeploymentIsDone(deploymentKey);

    final var targetPartitionCount = PARTITIONS_COUNT + 1;
    final var variableProvider = new CorrelationKeyVariableProvider();

    final var correlationKeyProvider = new CorrelationKeyVariableProvider();
    // Start some instances before scaling by publishing messages
    testCase.startProcessInstances(
        camundaClient, PARTITIONS_COUNT * 5, variableProvider.supplier(correlationKeyVariable));

    // when
    scaleToPartitions(targetPartitionCount);
    awaitScaleUpCompletion(targetPartitionCount);

    // Start instances after scaling by publishing messages
    testCase.startProcessInstances(
        camundaClient, targetPartitionCount * 5, variableProvider.supplier(correlationKeyVariable));

    // then - verify that message start events work and intermediate messages can be correlated
    correlationKeyProvider.correlateAllMessages(camundaClient, continueMessageName);
  }

  public void assertThatRoutingStateMatches(final RoutingState routingState) {
    Awaitility.await("until topology is restored correctly")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getRouting()).isNotNull();
              assertThat(topology.getRouting().getRequestHandling())
                  .isEqualTo(routingState.getRequestHandling());
              assertThat(topology.getRouting().getMessageCorrelation())
                  .isEqualTo(routingState.getMessageCorrelation());
            });
  }

  private void assertBackupIsCompleted(final long backupId) {
    Awaitility.await("until backup is ready")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              try {
                final var backupInfo = backupActuator.status(backupId);
                assertThat(backupInfo.getState()).isEqualTo(StateCode.COMPLETED);
              } catch (final Exception e) {
                LOG.error("Failed to get backup status for backupId: {}", backupId, e);
                fail(e);
              }
            });
  }

  private void restoreAllBrokers(final long backupId) throws IOException {
    for (final var broker : cluster.brokers().values()) {
      LOG.debug("Restoring broker: {}", broker.nodeId());
      final var dataFolder = Path.of(broker.brokerConfig().getData().getDirectory());
      FileUtil.deleteFolderIfExists(dataFolder);

      Files.createDirectories(dataFolder);
      try (final var restoreApp =
          new TestRestoreApp(broker.brokerConfig()).withBackupId(backupId)) {
        assertThatNoException().isThrownBy(restoreApp::start);
      }
      FileUtil.flushDirectory(dataFolder);
    }
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
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getRouting()).isNotNull();
              final var requestHandling = topology.getRouting().getRequestHandling();
              assertThat(requestHandling).isInstanceOf(RequestHandlingAllPartitions.class);
              final var allPartitions = (RequestHandlingAllPartitions) requestHandling;
              assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
            });
  }

  static class CorrelationKeyVariableProvider {

    final AtomicInteger messageId = new AtomicInteger();
    final ArrayBlockingQueue<Integer> correlationKeys = new ArrayBlockingQueue<>(1024);

    Supplier<Map<String, Object>> supplier(final String name) {
      return () -> {
        final var key = messageId.incrementAndGet();
        if (!correlationKeys.add(key)) {
          throw new IllegalStateException("Too many correlation keys created");
        }
        return Map.of(name, key);
      };
    }

    void correlateAllMessages(final CamundaClient camundaClient, final String messageName) {
      while (!correlationKeys.isEmpty()) {
        final var key = correlationKeys.poll();
        Awaitility.await("until process instance with variable" + key + " is started")
            .timeout(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(
                () -> {
                  // Broadcast continue signal to verify subscriptions are ready
                  assertThat(
                          camundaClient
                              .newCorrelateMessageCommand()
                              .messageName(messageName)
                              .correlationKey(String.valueOf(key))
                              .send()
                              .toCompletableFuture())
                      .succeedsWithin(Duration.ofSeconds(5))
                      .satisfies(r -> assertThat(r.getMessageKey()).isNotNull().isPositive());
                });
      }
    }
  }

  enum RestartTarget {
    PARTITION_1_LEADER,
    BOOTSTRAP_NODE;

    public TestStandaloneBroker restart(final TestCluster cluster) {
      return switch (this) {
        case PARTITION_1_LEADER -> cluster.leaderForPartition(1);
        case BOOTSTRAP_NODE -> cluster.brokers().get(MEMBER_0);
      };
    }
  }

  enum TestCase {
    MESSAGE,
    SIGNAL;

    String start() {
      return "start";
    }

    String messageName() {
      return "start";
    }

    String correlationKeyVariable() {
      return "correlationKey";
    }

    AbstractStartEventBuilder<?> setupStartEvent(final ProcessBuilder builder) {
      return switch (this) {
        case MESSAGE ->
            builder
                .startEvent(start())
                .message(
                    m ->
                        m.name(messageName())
                            .zeebeCorrelationKeyExpression(correlationKeyVariable()));
        case SIGNAL -> builder.startEvent(start()).signal(messageName());
      };
    }

    void startProcessInstances(
        final CamundaClient camundaClient,
        final int count,
        final Supplier<Map<String, Object>> correlationKeyProvider) {
      switch (this) {
        case MESSAGE -> {
          for (int i = 0; i < count; i++) {
            final var variables = correlationKeyProvider.get();
            camundaClient
                .newPublishMessageCommand()
                .messageName(messageName())
                .correlationKey(variables.keySet().iterator().next())
                .variables(variables)
                .send()
                .join();
          }
        }
        case SIGNAL -> {
          for (int i = 0; i < count; i++) {
            camundaClient
                .newBroadcastSignalCommand()
                .signalName(messageName())
                .variables(correlationKeyProvider.get())
                .send()
                .join();
          }
        }
        default -> throw new IllegalArgumentException("Invalid enum case " + this);
      }
    }
  }
}
