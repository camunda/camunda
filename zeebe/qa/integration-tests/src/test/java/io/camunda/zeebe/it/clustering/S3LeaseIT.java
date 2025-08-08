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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@ZeebeIntegration
@Testcontainers
public class S3LeaseIT {

  // TODO: Provide you AWS S3 credentials
  // Neither LocalStack S3 nor MinIO have strong consistence guarantees on atomic file operations
  public static final String ACCESS_KEY = "";
  public static final String SECRET_KEY = "";
  public static final Region REGION = Region.EU_NORTH_1;
  public static S3AsyncClient s3Client;
  private static final String BUCKET_NAME = "";

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withReplicationFactor(1)
          .withPartitionsCount(3)
          .withBrokerConfig(cfg -> cfg.withCreateSchema(false))
          .withEmbeddedGateway(false)
          .build();

  @BeforeEach
  void beforeAll() {
    final S3LeaseConfig config = new S3LeaseConfig();
    config.setBucketName(BUCKET_NAME);
    config.setAccessKey(ACCESS_KEY);
    config.setSecretKey(SECRET_KEY);
    config.setRegion(Optional.of(REGION.id()));
    s3Client =
        S3AsyncClient.builder()
            .region(Region.of(config.getRegion().get()))
            .credentialsProvider(
                () -> AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()))
            .build();
    if (s3Client.listBuckets().join().buckets().stream()
        .noneMatch(b -> b.name().equals(BUCKET_NAME))) {
      // Create bucket if it does not exist
      s3Client
          .createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build())
          .join();
    }
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
