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
import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private static final String TASK_ID_PROPERTY = "camunda.cluster.node-id-provider.s3.taskId";

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
                  app.withAdditionalProperties(
                      Map.of(
                          "camunda.data.secondary-storage.type",
                          "none",
                          "camunda.cluster.size",
                          CLUSTER_SIZE,
                          "camunda.cluster.node-id-provider.type",
                          "s3",
                          TASK_ID_PROPERTY,
                          UUID.randomUUID().toString(),
                          "camunda.cluster.node-id-provider.s3.bucketName",
                          BUCKET_NAME,
                          "camunda.cluster.node-id-provider.s3.leaseDuration",
                          LEASE_DURATION,
                          "camunda.cluster.node-id-provider.s3.endpoint",
                          S3.getEndpoint(),
                          "camunda.cluster.node-id-provider.s3.region",
                          S3.getRegion(),
                          "camunda.cluster.node-id-provider.s3.accessKey",
                          S3.getAccessKey(),
                          "camunda.cluster.node-id-provider.s3.secretKey",
                          S3.getSecretKey())))
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
                    b -> b.brokerConfig().getCluster().getNodeId(),
                    b -> b.property(TASK_ID_PROPERTY, String.class, null)));

    assertThat(s3NodeIdMapping).isEqualTo(configNodeIdMapping);

    // health check passes
    for (final var broker : testCluster.brokers().values()) {
      final var actuator = BrokerHealthActuator.of(broker);
      assertThatNoException().isThrownBy(actuator::ready);
      assertThatNoException().isThrownBy(actuator::live);
    }
  }

  private List<Lease> readLeases(final List<S3Object> objectsInBucket) throws IOException {
    final var leases = new ArrayList<Lease>();
    for (final var object : objectsInBucket) {
      final var lease = s3Client.getObject(b -> b.bucket(BUCKET_NAME).key(object.key()));
      final var payload = lease.readAllBytes();
      if (payload.length > 0) {
        final var parsed = Lease.fromJsonBytes(OBJECT_MAPPER, payload);
        if (parsed.isStillValid(System.currentTimeMillis(), LEASE_DURATION)) {
          leases.add(parsed);
        }
      }
    }

    return leases;
  }
}
