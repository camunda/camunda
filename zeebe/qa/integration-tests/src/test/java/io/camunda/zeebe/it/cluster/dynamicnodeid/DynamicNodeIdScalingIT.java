/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@ZeebeIntegration
@Timeout(120)
public class DynamicNodeIdScalingIT {

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices("s3")
          .withEnv("LS_LOG", "info");

  @AutoClose private static S3Client s3Client;
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);
  private static final int INITIAL_CLUSTER_SIZE = 1;
  private static final int PARTITIONS_COUNT = 3;
  private static final int TARGET_CLUSTER_SIZE = 3;
  private static final String CLUSTER_NAME = "dynamic-node-id-scaling-test";
  private final String bucketName = UUID.randomUUID().toString();
  private final List<AutoCloseable> resourcesToClose = new java.util.ArrayList<>();

  @BeforeAll
  static void beforeAll() {
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));
  }

  @BeforeEach
  void setup() {
    s3Client.createBucket(b -> b.bucket(bucketName));
  }

  @AfterEach
  void cleanup() throws Exception {
    final var objects = s3Client.listObjects(b -> b.bucket(bucketName));
    objects.contents().parallelStream()
        .forEach(obj -> s3Client.deleteObject(b -> b.bucket(bucketName).key(obj.key())));
    s3Client.deleteBucket(b -> b.bucket(bucketName));

    for (final var autoCloseable : resourcesToClose) {
      autoCloseable.close();
    }
  }

  private void configureBroker(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);

    cfg.getCluster().setSize(INITIAL_CLUSTER_SIZE);
    configureS3NodeIdProvider(cfg);
  }

  private void configureS3NodeIdProvider(final Camunda cfg) {
    cfg.getCluster().getNodeIdProvider().setType(Type.S3);
    final S3 s3 = cfg.getCluster().getNodeIdProvider().s3();
    s3.setTaskId(UUID.randomUUID().toString());
    s3.setBucketName(bucketName);
    s3.setLeaseDuration(LEASE_DURATION);
    s3.setEndpoint(S3.getEndpoint().toString());
    s3.setRegion(S3.getRegion());
    s3.setAccessKey(S3.getAccessKey());
    s3.setSecretKey(S3.getSecretKey());
  }

  private void assertConfigurationChangeCompleted(
      final ClusterActuator clusterActuator, final int targetClusterSize) {
    final var topology = clusterActuator.getTopology();

    // verify no pending changes
    assertThat(topology.getPendingChange()).isNull();

    // verify remaining broker is active
    final var activeBrokers =
        topology.getBrokers().stream()
            .filter(b -> b.getState() == BrokerStateCode.ACTIVE)
            .collect(Collectors.toList());
    assertThat(activeBrokers).hasSize(targetClusterSize);
  }

  @Nested
  class ScaleUp {
    @TestZeebe(autoStart = false)
    private final TestCluster testCluster =
        TestCluster.builder()
            .withName(CLUSTER_NAME)
            .withBrokersCount(INITIAL_CLUSTER_SIZE)
            .withPartitionsCount(PARTITIONS_COUNT)
            .withReplicationFactor(1)
            .withoutNodeId()
            .withNodeConfig(
                app ->
                    app.withProperty(
                            "camunda.data.secondary-storage.type", SecondaryStorageType.none.name())
                        .withUnifiedConfig(DynamicNodeIdScalingIT.this::configureBroker))
            .build();

    @Test
    public void shouldScaleClusterWithDynamicNodeIdProvider() {
      // given - start cluster with one broker
      testCluster.start();
      testCluster.awaitHealthyTopology();

      final var firstBroker = testCluster.brokers().values().iterator().next();
      final var initialContactPoint = firstBroker.address(TestZeebePort.CLUSTER);

      // when - start two new brokers with contact point to first broker
      final var secondBroker = getAdditionalBroker(initialContactPoint);
      final var thirdBroker = getAdditionalBroker(initialContactPoint);

      // Start in a separate thread as start() is blocking because node-id-provider cannot acquire a
      // lease until scale api is called.
      final var newBrokersStarted = new AtomicInteger();
      startBroker(secondBroker, newBrokersStarted);
      startBroker(thirdBroker, newBrokersStarted);

      // send scale request using ClusterActuator
      final var clusterActuator = ClusterActuator.of(firstBroker);
      final var scaleRequest =
          new ClusterConfigPatchRequest()
              .brokers(new ClusterConfigPatchRequestBrokers().count(TARGET_CLUSTER_SIZE));

      clusterActuator.patchCluster(scaleRequest, false, false);

      // then - verify scale operation completes
      Awaitility.await("Until scale operation completes")
          .atMost(Duration.ofMinutes(2))
          .pollInterval(Duration.ofSeconds(2))
          .untilAsserted(
              () -> assertConfigurationChangeCompleted(clusterActuator, TARGET_CLUSTER_SIZE));

      Awaitility.await("Start up of new brokers completes")
          .untilAsserted(
              () ->
                  assertThat(newBrokersStarted.get())
                      .describedAs("Two brokers have started")
                      .isEqualTo(2));
    }

    private static void startBroker(
        final TestStandaloneBroker secondBroker, final AtomicInteger newBrokersStarted) {
      Thread.ofVirtual()
          .start(
              () -> {
                secondBroker.start();
                newBrokersStarted.getAndIncrement();
              });
    }

    private TestStandaloneBroker getAdditionalBroker(final String initialContactPoint) {
      final var broker =
          new TestStandaloneBroker()
              .withProperty("camunda.data.secondary-storage.type", SecondaryStorageType.none.name())
              .withUnifiedConfig(
                  cfg -> {
                    cfg.getCluster().setName(CLUSTER_NAME);
                    cfg.getCluster().setInitialContactPoints(List.of(initialContactPoint));
                    configureBroker(cfg);
                  });
      resourcesToClose.add(broker);
      return broker;
    }
  }

  @Nested
  class ScaleDown {
    private static final int SCALE_DOWN_INITIAL_CLUSTER_SIZE = 3;
    private static final int SCALE_DOWN_TARGET_CLUSTER_SIZE = 1;

    @TestZeebe(autoStart = false)
    private final TestCluster testCluster =
        TestCluster.builder()
            .withName(CLUSTER_NAME)
            // Start a separate gateway as the brokers might shutdown during scale down.
            .withEmbeddedGateway(false)
            .withGatewaysCount(1)
            .withGatewayConfig(g -> g.withCreateSchema(false).withUnauthenticatedAccess())
            .withBrokersCount(SCALE_DOWN_INITIAL_CLUSTER_SIZE)
            .withPartitionsCount(PARTITIONS_COUNT)
            .withReplicationFactor(1)
            .withoutNodeId()
            .withNodeConfig(
                app ->
                    app.withUnifiedConfig(
                        cfg -> {
                          cfg.getCluster().setSize(SCALE_DOWN_INITIAL_CLUSTER_SIZE);
                          configureS3NodeIdProvider(cfg);
                        }))
            .build();

    @Test
    public void shouldScaleDownClusterFromThreeToOneBroker() {
      // given - start cluster with three brokers
      testCluster.start();
      testCluster.awaitHealthyTopology();

      final var clusterActuator = ClusterActuator.of(testCluster.anyGateway());

      // when - send scale down request
      final var scaleDownRequest =
          new ClusterConfigPatchRequest()
              .brokers(
                  new ClusterConfigPatchRequestBrokers().count(SCALE_DOWN_TARGET_CLUSTER_SIZE));

      clusterActuator.patchCluster(scaleDownRequest, false, false);

      // then - verify scale down operation completes
      Awaitility.await("Until scale down operation completes")
          .atMost(Duration.ofMinutes(2))
          .pollInterval(Duration.ofSeconds(2))
          .untilAsserted(
              () ->
                  assertConfigurationChangeCompleted(
                      clusterActuator, SCALE_DOWN_TARGET_CLUSTER_SIZE));

      Awaitility.await("Scaled down brokers are shutdown")
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () ->
                  assertThat(
                          testCluster.brokers().values().stream()
                              .filter(TestSpringApplication::isStarted)
                              .count())
                      .isEqualTo(1));
    }

    @Test
    void shouldScaleUpAgainAfterScaleDown() {
      // given
      shouldScaleDownClusterFromThreeToOneBroker();
      testCluster.awaitCompleteTopology(
          SCALE_DOWN_TARGET_CLUSTER_SIZE, PARTITIONS_COUNT, 1, Duration.ofSeconds(10));

      // when
      final var clusterActuator = ClusterActuator.of(testCluster.anyGateway());
      testCluster.brokers().values().stream()
          .filter(b -> !b.isStarted())
          .forEach(b -> Thread.ofVirtual().start(b::start));

      final var scaleUpRequest =
          new ClusterConfigPatchRequest()
              .brokers(
                  new ClusterConfigPatchRequestBrokers().count(SCALE_DOWN_INITIAL_CLUSTER_SIZE));

      clusterActuator.patchCluster(scaleUpRequest, false, false);

      // then
      Awaitility.await()
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () ->
                  assertConfigurationChangeCompleted(
                      clusterActuator, SCALE_DOWN_INITIAL_CLUSTER_SIZE));
      testCluster.awaitCompleteTopology(
          SCALE_DOWN_INITIAL_CLUSTER_SIZE, PARTITIONS_COUNT, 1, Duration.ofSeconds(10));
    }
  }
}
