/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.atomix.utils.AbstractIdentifier;
import io.camunda.zeebe.broker.clustering.mapper.S3LeaseConfig;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@ZeebeIntegration
@Testcontainers
public class S3LeaseIT {

  public static final String ACCESS_KEY = "letmein";
  public static final String SECRET_KEY = "letmein1234";
  public static final int S3_PORT = 9000;
  public static S3AsyncClient s3Client;
  private static final Logger LOG = LoggerFactory.getLogger(S3LeaseIT.class);
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> S3 =
      new GenericContainer<>(DockerImageName.parse("minio/minio"))
          .withCommand("server /data")
          .withExposedPorts(S3_PORT)
          .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
          .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
          .withEnv("MINIO_DOMAIN", "localhost")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/minio/health/ready")
                  .forPort(S3_PORT)
                  .withStartupTimeout(Duration.ofMinutes(1)));

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withGatewaysCount(1)
          .withReplicationFactor(2)
          .withPartitionsCount(3)
          .withEmbeddedGateway(false)
          .build();

  @BeforeEach
  void beforeAll() {
    final String s3Uri = "http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(S3_PORT));
    final S3LeaseConfig config = new S3LeaseConfig();
    config.setBucketName(BUCKET_NAME);
    config.setEndpoint(Optional.of(s3Uri));
    config.setAccessKey(ACCESS_KEY);
    config.setSecretKey(SECRET_KEY);
    config.setRegion(Optional.of(Region.US_EAST_1.id()));
    s3Client =
        S3AsyncClient.builder()
            .endpointOverride(URI.create(s3Uri))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                () -> AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()))
            .build();

    s3Client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
    cluster.brokers().values().forEach(broker -> broker.brokerConfig().setLeaseConfig(config));
    cluster.start().awaitCompleteTopology();
  }

  @Test
  void lease() {
    final Set<String> brokerIds =
        new HashSet<>(cluster.brokers().keySet().stream().map(AbstractIdentifier::id).toList());

    final List<String> claimedTaskIds = new ArrayList<>();
    brokerIds.forEach(
        brokerId -> {
          final var resp = s3Client.headObject(h -> h.bucket(BUCKET_NAME).key(brokerId)).join();
          assertThat(resp).isNotNull();
          assertThat(resp.hasMetadata()).isTrue();
          assertThat(resp.metadata()).containsKey("taskid");
          assertThat(resp.metadata().get("taskid")).isNotBlank();
          claimedTaskIds.add(resp.metadata().get("taskid"));
          assertThat(resp.metadata().get("expiry")).isNotBlank();
        });

    // Check that all brokers have different taskIds
    assertThat(claimedTaskIds.size()).isEqualTo(brokerIds.size());
    for (final String taskId : claimedTaskIds) {
      // Check that all taskIds are unique
      assertThat(claimedTaskIds.stream().filter(id -> id.equals(taskId)).count()).isOne();
    }
  }
}
