/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.clustering.mapper.NodeIdMapper;
import io.camunda.zeebe.broker.clustering.mapper.NodeInstance;
import io.camunda.zeebe.broker.clustering.mapper.S3LeaseConfig;
import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient.Lease;
import io.camunda.zeebe.broker.clustering.mapper.lease.S3Lease;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

public class S3LeaseComponentIT {

  // TODO: Provide you AWS S3 credentials
  // Neither LocalStack S3 nor MinIO have strong consistence guarantees on atomic file operations
  public static final String ACCESS_KEY = "";
  public static final String SECRET_KEY = "";
  public static final Region REGION = Region.EU_NORTH_1;
  public static S3AsyncClient s3Client;
  private static final String BUCKET_NAME = "";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int CLUSTER_SIZE = 30;
  private static final List<Integer> ids = IntStream.range(0, CLUSTER_SIZE).boxed().toList();
  ExecutorService executor = Executors.newFixedThreadPool(CLUSTER_SIZE);
  NodeIdMapper[] nodeIdMappers;

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
    //        if (s3Client.listBuckets().join().buckets().stream()
    //            .noneMatch(b -> b.name().equals(BUCKET_NAME))) {
    //          // Create bucket if it does not exist
    //          s3Client
    //              .createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build())
    //              .join();
    //        }

  }

  @BeforeEach
  void setup() {
    final var futures =
        ids.stream()
            .map(i -> CompletableFuture.supplyAsync(() -> createNodeIdMapper(i)))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();
    nodeIdMappers =
        Arrays.stream(futures)
            .map(CompletableFuture::join)
            .map(NodeIdMapper.class::cast)
            .toArray(NodeIdMapper[]::new);
  }

  @AfterEach
  void afterEach() {
    CloseHelper.closeAll(nodeIdMappers);
    executor.shutdownNow();
  }

  @AfterAll
  static void afterAll() {
    //    for (int i = 0; i < CLUSTER_SIZE; i++) {
    //      final int finalI = i;
    //      s3Client.deleteObject(b -> b.bucket(BUCKET_NAME).key(finalI + ".json")).join();
    //    }
  }

  @Test
  void lease() {
    // given
    // nodeMappers setup

    // when
    final var acquiredIds =
        CompletableFuture.allOf(
            Arrays.stream(nodeIdMappers)
                .map(m -> CompletableFuture.supplyAsync(m::start, executor))
                .toArray(CompletableFuture[]::new));

    // then
    Awaitility.await().untilAsserted(() -> assertThat(acquiredIds).isCompleted());

    ids.forEach(
        brokerId -> {
          Awaitility.await("until the lease has been held for node " + brokerId)
              .atMost(Duration.ofSeconds(30))
              .untilAsserted(
                  () -> {
                    final var resp =
                        s3Client
                            .getObject(
                                h -> h.bucket(BUCKET_NAME).key(S3Lease.objectKey(brokerId)),
                                AsyncResponseTransformer.toBytes())
                            .join();
                    assertThat(resp).isNotNull();
                    assertThatNoException()
                        .isThrownBy(
                            () -> {
                              final var body =
                                  Lease.fromJsonBytes(OBJECT_MAPPER, resp.asByteArray());
                              System.out.println(String.valueOf(body));
                              assertThat(body.nodeInstance().version()).isEqualTo(1);
                            });
                    final var response = resp.response();
                    assertThat(response.hasMetadata()).isTrue();
                    assertThat(response.metadata()).containsKey("taskid");
                    assertThat(response.metadata().get("taskid")).isNotBlank();
                    assertThat(response.metadata().get("expiry")).isNotBlank();
                  });
        });
  }

  @Test
  void acquireANewLeaseWhenNodeRestarts() {
    // given
    // when
    final var previousTimestamp = nodeIdMappers[0].expiresAt();
    nodeIdMappers[0].close();
    nodeIdMappers[0] = createNodeIdMapper(0);

    final var id = nodeIdMappers[0].start();

    // then
    assertThat(id).isEqualTo(new NodeInstance(0, 2));
    assertThat(nodeIdMappers[0].expiresAt()).isGreaterThan(previousTimestamp);
  }

  private NodeIdMapper createNodeIdMapper(final int i) {
    final var taskId = NodeIdMapper.randomTaskId();
    final var lease = new S3Lease(s3Client, BUCKET_NAME, taskId, CLUSTER_SIZE, Clock.systemUTC());
    return new NodeIdMapper(
        lease, () -> System.err.println("Failed to renew lease for taskId " + taskId));
  }
}
