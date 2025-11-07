/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.bytebuddy.utility.dispatcher.JavaDispatcher.Container;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@ZeebeIntegration
public class DynamicNodeIdTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);

  @org.testcontainers.junit.jupiter.Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client s3Client;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneApplication;

  private String bucketName;
  private String taskId;
  private int clusterSize;

  @Container
  @BeforeEach
  public void setup() {
    bucketName = UUID.randomUUID().toString();

    taskId = UUID.randomUUID().toString();
    clusterSize = 3;
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));

    // bucket must be created before the application is started
    s3Client.createBucket(b -> b.bucket(bucketName));

    testStandaloneApplication =
        new TestStandaloneBroker()
            .withCreateSchema(false)
            .withAdditionalProperties(
                // Disable secondary storage
                Map.of(
                    "camunda.rest.query.enabled",
                    "false",
                    "camunda.persistent.sessions.enabled",
                    "false",
                    "camunda.data.secondary-storage.type",
                    "none",
                    "camunda.operate.importerenabled",
                    "false",
                    "camunda.operate.archiverenabled",
                    "false",
                    "camunda.tasklist.importerenabled",
                    "false",
                    "camunda.tasklist.archiverenabled",
                    "false"))
            .withAdditionalProperties(
                Map.of(
                    "camunda.cluster.size",
                    clusterSize,
                    "camunda.cluster.dynamic-node-id.type",
                    "s3",
                    "camunda.cluster.dynamic-node-id.s3.taskId",
                    taskId,
                    "camunda.cluster.dynamic-node-id.s3.bucketName",
                    bucketName,
                    "camunda.cluster.dynamic-node-id.s3.leaseDuration",
                    LEASE_DURATION,
                    "camunda.cluster.dynamic-node-id.s3.endpoint",
                    S3.getEndpoint(),
                    "camunda.cluster.dynamic-node-id.s3.region",
                    S3.getRegion(),
                    "camunda.cluster.dynamic-node-id.s3.accessKey",
                    S3.getAccessKey(),
                    "camunda.cluster.dynamic-node-id.s3.secretKey",
                    S3.getSecretKey()));

    testStandaloneApplication.start();
  }

  @Test
  public void shouldStartAppCorrectlyAndAcquireALease() throws IOException {
    final var objectsInBucket = s3Client.listObjects(b -> b.bucket(bucketName)).contents();

    Awaitility.await("Repository has been initialized")
        .untilAsserted(() -> assertThat(objectsInBucket).hasSize(clusterSize));

    final var leases = new ArrayList<Lease>();
    for (final var object : objectsInBucket) {
      final var lease = s3Client.getObject(b -> b.bucket(bucketName).key(object.key()));
      final var payload = lease.readAllBytes();
      if (payload.length > 0) {
        final var parsed = Lease.fromJsonBytes(OBJECT_MAPPER, payload);
        if (parsed.isStillValid(System.currentTimeMillis(), LEASE_DURATION)
            && parsed.taskId().equals(taskId)) {
          leases.add(parsed);
        }
      }
    }

    assertThat(leases).hasSize(1);
  }
}
