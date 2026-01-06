/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.it.util.TestHelper;
import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

@Testcontainers
@ZeebeIntegration
public class DynamicNodeIdIT {
  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client s3Client;
  private static final String BUCKET_NAME = UUID.randomUUID().toString();
  private static final int CLUSTER_SIZE = 3;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);

  @TestZeebe
  private final TestCluster testCluster =
      TestCluster.builder()
          .withName("node-id-provider-test")
          .withBrokersCount(CLUSTER_SIZE)
          .withPartitionsCount(1)
          .withReplicationFactor(CLUSTER_SIZE)
          .withoutNodeId()
          .withNodeConfig(
              app ->
                  app.withProperty(
                          "camunda.data.secondary-storage.type", SecondaryStorageType.none.name())
                      .withUnifiedConfig(
                          cfg -> {
                            cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                            cfg.getCluster().setSize(CLUSTER_SIZE);
                            cfg.getCluster().getNodeIdProvider().setType(Type.S3);
                            final S3 s3 = cfg.getCluster().getNodeIdProvider().s3();
                            s3.setTaskId(UUID.randomUUID().toString());
                            s3.setBucketName(BUCKET_NAME);
                            s3.setLeaseDuration(LEASE_DURATION);
                            s3.setEndpoint(S3.getEndpoint().toString());
                            s3.setRegion(S3.getRegion());
                            s3.setAccessKey(S3.getAccessKey());
                            s3.setSecretKey(S3.getSecretKey());
                          }))
          .build();

  @BeforeAll
  // Setup as static so it's done before the cluster
  public static void setupAll() {
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));

    // bucket must be created before the application is started
    s3Client.createBucket(b -> b.bucket(BUCKET_NAME));
  }

  @Test
  public void shouldStartAppCorrectlyAndAcquireALease() throws IOException {
    // then
    final var objectsInBucket = s3Client.listObjects(b -> b.bucket(BUCKET_NAME)).contents();

    assertThat(objectsInBucket).hasSize(CLUSTER_SIZE);
    final var leases = readLeases(objectsInBucket);

    // Verify the mapping nodeId -> taskId is consistent with the nodeId from the cluster
    final var s3NodeIdMapping =
        leases.stream().collect(Collectors.toMap(l -> l.nodeInstance().id(), Lease::taskId));

    final var configNodeIdMapping =
        testCluster.brokers().values().stream()
            .collect(
                Collectors.toMap(
                    // Get the nodeId from BrokerBasedProperties instead of the unified
                    // configuration, because the nodeId is assigned by the NodeIdProvider in
                    // BrokerBasedConfiguration if nodeId is null
                    b -> b.bean(BrokerBasedProperties.class).getCluster().getNodeId(),
                    b ->
                        b.unifiedConfig()
                            .getCluster()
                            .getNodeIdProvider()
                            .s3()
                            .getTaskId()
                            .orElseThrow()));

    assertThat(s3NodeIdMapping).isEqualTo(configNodeIdMapping);

    // health check passes
    for (final var broker : testCluster.brokers().values()) {
      final var actuator = BrokerHealthActuator.of(broker);
      assertThatNoException().isThrownBy(actuator::ready);
      assertThatNoException().isThrownBy(actuator::live);
    }
  }

  @Test
  public void shouldReacquireSameLeaseAfterGracefulRestart() {
    final var broker = testCluster.brokers().values().iterator().next();
    final int nodeIdBeforeRestart =
        broker.bean(BrokerBasedProperties.class).getCluster().getNodeId();
    final Lease leaseBeforeRestart = awaitValidLease(nodeIdBeforeRestart);

    // create some state, to ensure the cluster continues functioning after restart
    try (final CamundaClient client = testCluster.newClientBuilder().build()) {
      TestHelper.deployResource(client, "process/service_tasks_v1.bpmn");
      TestHelper.startProcessInstance(client, "service_tasks_v1");
    }

    broker.stop();

    // the S3 lease should be released on graceful shutdown (empty payload)
    Awaitility.await("Until lease is released")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(readLeaseObjectBytes(nodeIdBeforeRestart)).isEmpty());

    broker.start().await(TestHealthProbe.READY);
    testCluster.awaitCompleteTopology();

    final int nodeIdAfterRestart =
        broker.bean(BrokerBasedProperties.class).getCluster().getNodeId();
    assertThat(nodeIdAfterRestart).isEqualTo(nodeIdBeforeRestart);

    final Lease leaseAfterRestart = awaitValidLease(nodeIdAfterRestart);
    assertThat(leaseAfterRestart.nodeInstance().id()).isEqualTo(nodeIdBeforeRestart);
    assertThat(leaseAfterRestart.nodeInstance().version().version())
        .isGreaterThan(leaseBeforeRestart.nodeInstance().version().version());
    assertThat(leaseAfterRestart.taskId()).isEqualTo(leaseBeforeRestart.taskId());

    // no data corruption: the process is still executable after restart
    try (final CamundaClient client = testCluster.newClientBuilder().build()) {
      assertThatNoException()
          .isThrownBy(() -> TestHelper.startProcessInstance(client, "service_tasks_v1"));
    }
  }

  private List<Lease> readLeases(final List<S3Object> objectsInBucket) throws IOException {
    final var leases = new ArrayList<Lease>();
    for (final var object : objectsInBucket) {
      final var lease = s3Client.getObject(b -> b.bucket(BUCKET_NAME).key(object.key()));
      final var payload = lease.readAllBytes();
      if (payload.length > 0) {
        final var parsed = Lease.fromJsonBytes(OBJECT_MAPPER, payload);
        if (parsed.isStillValid(System.currentTimeMillis())) {
          leases.add(parsed);
        }
      }
    }

    return leases;
  }

  private byte[] readLeaseObjectBytes(final int nodeId) throws IOException {
    try (final var stream =
        s3Client.getObject(b -> b.bucket(BUCKET_NAME).key(S3NodeIdRepository.objectKey(nodeId)))) {
      return stream.readAllBytes();
    }
  }

  private Lease awaitValidLease(final int nodeId) {
    final AtomicReference<Lease> lease = new AtomicReference<>();
    Awaitility.await("Until lease is valid")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var bytes = readLeaseObjectBytes(nodeId);
              assertThat(bytes).isNotEmpty();
              final var parsed = Lease.fromJsonBytes(OBJECT_MAPPER, bytes);
              assertThat(parsed.isStillValid(System.currentTimeMillis())).isTrue();
              lease.set(parsed);
            });

    return lease.get();
  }
}
