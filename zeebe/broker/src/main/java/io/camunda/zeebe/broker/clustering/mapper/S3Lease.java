/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Lease {

  private final S3AsyncClient client;
  private final String leaseKey;
  private String bucketName;
  private int clusterSize;

  public S3Lease(final S3LeaseConfig config, final String taskId) {

    /**
     * 0 -> A123 1 -> B345 2 -> C567 3 -> {}
     *
     * <p>Key: basepath/nodeId : Value -> who has the lease, the tiemstamp
     */
    client = buildClient(config);
    leaseKey = config.basePath().map(basePath -> basePath + "/lease").orElse("lease");
  }

  public int acquireLease(final String taskId) {
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      final String objectKey = String.valueOf(nodeId);

      // 1. Get the current ETag
      final var headResponse =
          client
              .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
              .join();

      final var currentLeaseHolder =
          headResponse.metadata().get("taskId"); // Get the taskId from metadata
      final var expiry =
          headResponse
              .metadata()
              .getOrDefault(
                  "expiry",
                  String.valueOf(
                      Instant.now().minusSeconds(1))); // Get the expiry timestamp from metadata
      final boolean isCurrentLeaseExpired =
          Instant.ofEpochMilli(Long.valueOf(expiry)).isBefore(Instant.now());

      if (currentLeaseHolder == null || currentLeaseHolder.isBlank() || isCurrentLeaseExpired) {
        final String currentETag = headResponse.eTag();

        if (atomicAcquireLease(taskId, objectKey, currentETag)) {
          return nodeId; // Lease acquired successfully
        }
      }
    }

    return -1; // return the nodeId for which the lease is acquired
  }

  private boolean atomicAcquireLease(
      final String taskId, final String objectKey, final String currentETag) {
    // 2. Attempt atomic update
    final PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType("application/json")
            .metadata(
                Map.of(
                    "taskId",
                    taskId,
                    "expiry",
                    String.valueOf(
                        Instant.now()
                            .plusSeconds(60)
                            .toEpochMilli()))) // Store the taskId in metadata
            .ifMatch(currentETag)
            .build();

    try {
      client.putObject(putRequest, AsyncRequestBody.fromString(taskId)).join();
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  public boolean renewLease(final int nodeId, final String taskId) {

    final String objectKey = String.valueOf(nodeId);

    final var headResponse =
        client
            .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .join();

    final var currentLeaseHolder = headResponse.metadata().get("taskId");
    if (taskId.equals(currentLeaseHolder)) {
      return atomicAcquireLease(taskId, objectKey, headResponse.eTag());
    }

    return false;
  }

  public void releaseLease(final int nodeId, final String taskId) {
    final String objectKey = String.valueOf(nodeId);

    final var headResponse =
        client
            .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .join();

    final var currentLeaseHolder = headResponse.metadata().get("taskId");
    if (taskId.equals(currentLeaseHolder)) {
      // delete the object
      final DeleteObjectRequest request =
          DeleteObjectRequest.builder()
              .bucket(bucketName)
              .key(objectKey)
              .ifMatch(headResponse.eTag())
              .build();

      try {
        client.deleteObject(request).join();
      } catch (final Exception e) {
        // Nothing
      }
    }
  }

  public static S3AsyncClient buildClient(final S3LeaseConfig config) {
    final var builder = S3AsyncClient.builder();

    // Enable auto-tuning of various parameters based on the environment
    builder.defaultsMode(DefaultsMode.AUTO);

    builder.httpClient(
        NettyNioAsyncHttpClient.builder()

            // We'd rather wait longer for a connection than have a failed backup. This helps in
            // smoothing out spikes when taking a backup.
            .connectionAcquisitionTimeout(config.connectionAcquisitionTimeout())
            .build());

    builder.overrideConfiguration(cfg -> cfg.retryStrategy(RetryMode.ADAPTIVE_V2));

    config.endpoint().ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
    config.region().ifPresent(region -> builder.region(Region.of(region)));
    config
        .credentials()
        .ifPresent(
            credentials ->
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            credentials.accessKey(), credentials.secretKey()))));
    config
        .apiCallTimeout()
        .ifPresent(timeout -> builder.overrideConfiguration(cfg -> cfg.apiCallTimeout(timeout)));
    return builder.build();
  }
}
