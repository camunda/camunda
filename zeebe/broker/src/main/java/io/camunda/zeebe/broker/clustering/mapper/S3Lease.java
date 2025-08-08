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
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Lease {
  public static final int LEASE_EXPIRY_SECONDS = 60;
  private static final String TASK_ID_METADATA_KEY = "taskid";
  private static final String EXPIRY_METADATA_KEY = "expiry";
  private static final Logger LOG = LoggerFactory.getLogger(S3Lease.class);
  private final S3AsyncClient client;
  private final String bucketName;
  private final int clusterSize;
  private final String taskId;

  public S3Lease(final S3LeaseConfig config, final String taskId, final int clusterSize) {
    this.taskId = taskId;
    client = buildClient(config);
    bucketName = config.bucketName();
    this.clusterSize = clusterSize;
    initializeFiles();
  }

  public void initializeFiles() {
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      final String objectKey = String.valueOf(nodeId);
      final PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(objectKey)
              .contentType("application/json")
              .ifNoneMatch("*")
              .build();

      try {
        final var res = client.putObject(putRequest, AsyncRequestBody.fromString(taskId)).join();
        LOG.info("File created for nodeId {}: {}", nodeId, res.eTag());
      } catch (final Exception e) {
        LOG.error(
            "File creation failed for nodeId {}: {}",
            nodeId,
            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      }
    }
  }

  public int acquireLease() {
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      final String objectKey = String.valueOf(nodeId);

      // 1. Get the current ETag
      final var headResponse =
          client
              .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
              .join();

      final var currentLeaseHolder =
          headResponse.metadata().get(TASK_ID_METADATA_KEY); // Get the taskId from metadata
      final var expiry =
          headResponse
              .metadata()
              .getOrDefault(
                  EXPIRY_METADATA_KEY,
                  String.valueOf(
                      Instant.now()
                          .minusSeconds(1)
                          .toEpochMilli())); // Get the expiry timestamp from metadata
      final boolean isCurrentLeaseExpired =
          Instant.ofEpochMilli(Long.parseLong(expiry)).isBefore(Instant.now());

      if (currentLeaseHolder == null || currentLeaseHolder.isBlank() || isCurrentLeaseExpired) {
        final String currentETag = headResponse.eTag();

        if (atomicAcquireLease(taskId, objectKey, currentETag)) {
          return nodeId; // Lease acquired successfully
        }
      }
    }
    throw new LeaseException("Could not acquire lease for any nodeId in the cluster");
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
                    TASK_ID_METADATA_KEY,
                    taskId,
                    EXPIRY_METADATA_KEY,
                    String.valueOf(
                        Instant.now()
                            .plusSeconds(LEASE_EXPIRY_SECONDS)
                            .toEpochMilli()))) // Store the taskId in metadata
            .ifMatch(currentETag)
            .build();

    try {
      LOG.info("Attempting to acquire lease for nodeId {} with taskId {}", objectKey, taskId);
      client.putObject(putRequest, AsyncRequestBody.fromString(taskId)).join();
      return true;
    } catch (final Exception e) {
      LOG.error(
          "Failed to acquire lease for nodeId {}: {}",
          objectKey,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return false;
    }
  }

  public boolean renewLease(final int nodeId) {

    final String objectKey = String.valueOf(nodeId);
    final HeadObjectResponse headResponse;
    try {
      headResponse =
          client
              .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
              .join();
    } catch (final CompletionException e) {
      LOG.error(
          "Failed to renew lease for nodeId {}:{} {}",
          objectKey,
          taskId,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return false;
    }

    final var currentLeaseHolder = headResponse.metadata().get(TASK_ID_METADATA_KEY);
    if (taskId.equals(currentLeaseHolder)) {
      return atomicAcquireLease(taskId, objectKey, headResponse.eTag());
    }

    return false;
  }

  public void releaseLease(final int nodeId) {
    final String objectKey = String.valueOf(nodeId);

    final var headResponse =
        client
            .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .join();

    final var currentLeaseHolder = headResponse.metadata().get(TASK_ID_METADATA_KEY);
    if (taskId.equals(currentLeaseHolder)) {
      // delete the object
      final PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(objectKey)
              .contentType("application/json")
              .metadata(
                  Map.of(
                      TASK_ID_METADATA_KEY,
                      "",
                      EXPIRY_METADATA_KEY,
                      String.valueOf(
                          Instant.now()
                              .minusSeconds(1)
                              .toEpochMilli()))) // Store the taskId in metadata
              .ifMatch(headResponse.eTag())
              .build();

      client.putObject(putRequest, AsyncRequestBody.fromString(taskId)).join();
      LOG.info("Successfully release lease for nodeId {}: {}", objectKey, taskId);
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
    if (config.getAccessKey() == null || config.getSecretKey() == null) {
      LOG.warn(
          "S3 lease configuration is missing access key or secret key. "
              + "This may lead to authentication issues when trying to acquire leases.");
    } else {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())));
    }
    config
        .apiCallTimeout()
        .ifPresent(timeout -> builder.overrideConfiguration(cfg -> cfg.apiCallTimeout(timeout)));
    return builder.build();
  }
}
