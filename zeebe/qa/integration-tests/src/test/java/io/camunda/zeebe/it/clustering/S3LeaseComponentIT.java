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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

public class S3LeaseComponentIT {

  // TODO: Provide you AWS S3 credentials
  // Neither LocalStack S3 nor MinIO have strong consistence guarantees on atomic file operations
  public static final String ACCESS_KEY = System.getenv("AWS_ACCESS_KEY_ID");
  public static final String SECRET_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
  public static final Region REGION = Region.EU_NORTH_1;
  public static S3AsyncClient s3Client;
  private static final String BUCKET_NAME = System.getenv("AWS_BUCKET_NAME");
  private static final Duration LEASE_EXPIRY_DURATION = Duration.ofSeconds(20);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int CLUSTER_SIZE = 30;
  private static final List<Integer> IDS = IntStream.range(0, CLUSTER_SIZE).boxed().toList();
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
    for (int i = 0; i < CLUSTER_SIZE; i++) {
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(S3Lease.objectKey(i)).build());
      } catch (final Exception e) {
        System.err.println("failed to delete object for node=" + i);
      }
    }
    final var futures =
        IDS.stream()
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
    startMappers();
    // nodeMappers setup

    // when

    IDS.forEach(
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
    startMappers();
    // when
    final var previousTimestamp = nodeIdMappers[0].expiresAt();
    final var previousId = nodeIdMappers[0].getNodeInstance();
    nodeIdMappers[0].close();
    nodeIdMappers[0] = createNodeIdMapper(0);

    final var id = nodeIdMappers[0].start();

    // then
    assertThat(id).isEqualTo(previousId.nextVersion());
    assertThat(nodeIdMappers[0].expiresAt()).isGreaterThan(previousTimestamp);
  }

  @Test
  void shouldRenewLeaseConstantly() {
    // given
    startMappers();
    Awaitility.await("Until all leases are acquired")
        .untilAsserted(
            () -> assertThat(expirations()).allSatisfy(l -> assertThat(l).isGreaterThan(0)));
    final var timestamps = expirations();

    // then
    Awaitility.await("All leases have been renewed")
        .atMost(LEASE_EXPIRY_DURATION.multipliedBy(4))
        .untilAsserted(
            () -> {
              final var newTimestamps = expirations();
              for (int i = 0; i < CLUSTER_SIZE; i++) {
                assertThat(newTimestamps.get(i)).isGreaterThan(timestamps.get(i));
              }
            });
  }

  @Test
  void shouldEventuallyBecomeReady() {

    // given
    startMappers();
    // nodeMappers setup

    // when
    Awaitility.await("Until all leases are acquired")
        .untilAsserted(
            () -> {
              assertThat(nodeIdMappers)
                  .allSatisfy(
                      m -> assertThat(m.waitUntilReady()).succeedsWithin(Duration.ofSeconds(30)));
            });
  }

  private NodeIdMapper createNodeIdMapper(final int i) {
    final var taskId = NodeIdMapper.randomTaskId();
    final var lease =
        new S3Lease(
            s3Client, BUCKET_NAME, taskId, CLUSTER_SIZE, LEASE_EXPIRY_DURATION, Clock.systemUTC());
    return new NodeIdMapper(
        lease,
        () -> System.err.println("Failed to renew lease for taskId " + taskId),
        CLUSTER_SIZE);
  }

  private List<Long> expirations() {
    return Arrays.stream(nodeIdMappers).map(NodeIdMapper::expiresAt).toList();
  }

  private void startMappers() {
    final var acquiredIds =
        CompletableFuture.allOf(
            Arrays.stream(nodeIdMappers)
                .map(m -> CompletableFuture.supplyAsync(m::start, executor))
                .toArray(CompletableFuture[]::new));

    // then
    Awaitility.await().untilAsserted(() -> assertThat(acquiredIds).isCompleted());
  }
}
