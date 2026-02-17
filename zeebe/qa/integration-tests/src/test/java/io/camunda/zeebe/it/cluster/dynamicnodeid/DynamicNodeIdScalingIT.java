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
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

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
  private static final String BUCKET_NAME = UUID.randomUUID().toString();
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);
  private static final int INITIAL_CLUSTER_SIZE = 1;
  private static final int PARTITIONS_COUNT = 3;
  private static final int TARGET_CLUSTER_SIZE = 3;

  private static final String CLUSTER_NAME = "dynamic-node-id-scaling-test";

  private final List<AutoCloseable> resourcesToClose = new java.util.ArrayList<>();

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
                      .withUnifiedConfig(DynamicNodeIdScalingIT::configureBroker))
          .build();

  @AfterEach
  void cleanup() throws Exception {
    final var objects = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));
    objects.contents().parallelStream()
        .forEach(obj -> s3Client.deleteObject(b -> b.bucket(BUCKET_NAME).key(obj.key())));

    for (final var autoCloseable : resourcesToClose) {
      autoCloseable.close();
    }
  }

  @BeforeAll
  static void setupAll() {
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));

    s3Client.createBucket(b -> b.bucket(BUCKET_NAME));
  }

  @Test
  public void shouldScaleClusterWithDynamicNodeIdProvider() {
    // given - start cluster with one broker
    testCluster.start();
    testCluster.awaitHealthyTopology();

    final var firstBroker = testCluster.brokers().values().iterator().next();
    final var initialContactPoint = firstBroker.address(TestZeebePort.CLUSTER);

    // verify initial state - one lease object
    Awaitility.await("Until initial broker has a valid lease")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(getLeasesCount()).isEqualTo(1));

    // when - start two new brokers with contact point to first broker
    final var secondBroker = getAdditionalBroker(initialContactPoint);
    final var thirdBroker = getAdditionalBroker(initialContactPoint);

    // Start in a separate thread as start() is blocking because node-id-provider cannot acquire a
    // lease until scale api is called.
    final var thread1 = Thread.ofVirtual().start(secondBroker::start);
    final var thread2 = Thread.ofVirtual().start(thirdBroker::start);

    // send scale request using ClusterActuator
    final var clusterActuator = ClusterActuator.of(firstBroker);
    final var scaleRequest =
        new ClusterConfigPatchRequest()
            .brokers(new ClusterConfigPatchRequestBrokers().count(TARGET_CLUSTER_SIZE));

    clusterActuator.patchCluster(scaleRequest, false, false);

    // verify second broker has acquired a lease
    Awaitility.await("Until new brokers has a valid lease")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(getLeasesCount()).isEqualTo(TARGET_CLUSTER_SIZE));

    // then - verify scale operation completes
    Awaitility.await("Until scale operation completes")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getBrokers()).hasSize(TARGET_CLUSTER_SIZE);

              // verify no pending changes
              assertThat(topology.getPendingChange()).isNull();

              // verify all brokers are active
              final var activeBrokers =
                  topology.getBrokers().stream()
                      .filter(b -> b.getState() == BrokerStateCode.ACTIVE)
                      .collect(Collectors.toList());
              assertThat(activeBrokers).hasSize(TARGET_CLUSTER_SIZE);

              // verify partitions are distributed
              for (final BrokerState broker : topology.getBrokers()) {
                assertThat(broker.getPartitions()).isNotEmpty();
              }
            });

    assertThat(thread1.isAlive()).describedAs("Startup of secondBroker completes").isFalse();
    assertThat(thread2.isAlive()).describedAs("Startup of thirdBroker completes").isFalse();
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

  private static void configureBroker(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);

    cfg.getCluster().setSize(INITIAL_CLUSTER_SIZE);
    cfg.getCluster().getNodeIdProvider().setType(Type.S3);
    final S3 s3 = cfg.getCluster().getNodeIdProvider().s3();
    s3.setTaskId(UUID.randomUUID().toString());
    s3.setBucketName(BUCKET_NAME);
    s3.setLeaseDuration(LEASE_DURATION);
    s3.setEndpoint(S3.getEndpoint().toString());
    s3.setRegion(S3.getRegion());
    s3.setAccessKey(S3.getAccessKey());
    s3.setSecretKey(S3.getSecretKey());
  }

  private int getLeasesCount() throws IOException {
    return (int)
        s3Client.listObjects(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .filter(key -> key.matches("\\d+\\.json"))
            .count();
  }
}
