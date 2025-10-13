/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper.lease;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.clustering.mapper.NodeInstance;
import io.camunda.zeebe.broker.clustering.mapper.S3LeaseConfig;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Lease extends AbstractLeaseClient {
  public static final int LEASE_EXPIRY_SECONDS = 60;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String TASK_ID_METADATA_KEY = "taskid";
  private static final String EXPIRY_METADATA_KEY = "expiry";
  private static final String NODE_VERSION_METADATA_KEY = "nodeVersion";

  private static final Logger LOG = LoggerFactory.getLogger(S3Lease.class);
  private final S3AsyncClient client;
  private final String bucketName;
  private final Clock clock;
  // Mutable state
  private String currentETag;

  @VisibleForTesting
  public S3Lease(
      final S3AsyncClient client,
      final String bucketName,
      final String taskId,
      final int clusterSize,
      final Clock clock) {
    super(clusterSize, taskId, Clock.systemUTC(), Duration.ofSeconds(LEASE_EXPIRY_SECONDS));
    this.client = client;
    this.bucketName = bucketName;
    this.clock = clock;
  }

  public S3Lease(
      final S3LeaseConfig config, final String taskId, final int clusterSize, final Clock clock) {
    super(clusterSize, taskId, clock, Duration.ofSeconds(LEASE_EXPIRY_SECONDS));
    client = buildClient(config);
    bucketName = config.bucketName();
    this.clock = clock;
  }

  private Lease atomicAcquireLease(final Lease lease, final boolean isRenew) {
    // 2. Attempt atomic update
    if (isRenew && currentLease == null) {
      throw new IllegalStateException("Cannot acquire a lease, current one is empty");
    }
    final var nodeInstance = lease.nodeInstance();
    final var objectKey = objectKey(nodeInstance.id());
    final var nextLease = lease.renew(leaseExpirationDuration.toMillis());
    final var nodeVersion = nodeInstance.version();
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
                    String.valueOf(nextLease.timestamp()),
                    NODE_VERSION_METADATA_KEY,
                    String.valueOf(nodeVersion))) // Store the taskId in metadata
            .ifMatch(currentETag)
            .build();

    try {
      LOG.info(
          "Attempting to acquire(renew={}) lease for nodeId {} with taskId {}",
          Boolean.valueOf(isRenew),
          Integer.valueOf(nodeInstance.id()),
          taskId);
      final var response =
          client
              .putObject(
                  putRequest, AsyncRequestBody.fromBytes(nextLease.toJsonBytes(OBJECT_MAPPER)))
              .join();
      currentLease = nextLease;
      currentETag = response.eTag();
      return currentLease;
    } catch (final Exception e) {
      LOG.warn(
          "Failed to acquire(renew={}) lease for nodeId {}: {}",
          Boolean.valueOf(isRenew),
          Integer.valueOf(nodeInstance.id()),
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return null;
    }
  }

  @Override
  public void releaseLease() {
    if (currentLease == null) {
      return;
    }
    final var objectKey = objectKey(currentLease.nodeInstance().id());
    try {
      final var headResponse =
          client
              .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
              .join();
      final var currentLeaseHolder = headResponse.metadata().get(TASK_ID_METADATA_KEY);
      if (taskId.equals(currentLeaseHolder)) {
        // delete the object
        final var expiryInThePast = String.valueOf(Instant.now().minusSeconds(1).toEpochMilli());
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
                        expiryInThePast,
                        NODE_VERSION_METADATA_KEY,
                        String.valueOf(currentLease.nodeInstance().version())))
                .ifMatch(headResponse.eTag())
                .build();

        try {
          client.putObject(putRequest, AsyncRequestBody.empty()).join();
          LOG.info("Successfully release lease for nodeId {}", objectKey);
        } catch (final Exception e) {
          LOG.warn("Failed to actually release the lease for nodeId {}", objectKey);
        }
      }
    } catch (final Exception e) {
      LOG.warn("Head request failed for objectKey {}", objectKey);
    }
  }

  @Override
  public Lease tryAcquireLease(final int nodeId) {
    final String objectKey = objectKey(nodeId);

    // 1. Get the current ETag
    final var headResponse =
        client
            .headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .join();

    final var currentLeaseHolder =
        headResponse.metadata().get(TASK_ID_METADATA_KEY); // Get the taskId from metadata
    final var currentNodeVersionStr = headResponse.metadata().get(NODE_VERSION_METADATA_KEY);
    // Start
    final var currentNodeVersion =
        currentNodeVersionStr != null ? Integer.valueOf(currentNodeVersionStr) : 0;

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
      currentETag = headResponse.eTag();
      // always increase the version when acquiring a new lease
      final var nodeInstance = new NodeInstance(nodeId, currentNodeVersion).nextVersion();
      final var lease = new Lease(taskId, Long.parseLong(expiry), nodeInstance);

      return atomicAcquireLease(lease, false);
    }
    return null;
  }

  @Override
  public void initializeForNode(final int nodeId) {
    final PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey(nodeId))
            .contentType("application/json")
            .ifNoneMatch("*")
            .build();

    try {
      LOG.info("Creating file for node {}", nodeId);
      final var res = client.putObject(putRequest, AsyncRequestBody.empty()).join();
      LOG.debug("File created for nodeId {}: {}", nodeId, res.eTag());
    } catch (final Exception e) {
      LOG.debug(
          "File creation failed for nodeId {}: {}",
          nodeId,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
    }
  }

  @Override
  public Lease renewCurrentLease() {
    if (currentETag == null) {
      throw new IllegalStateException(
          "Cannot renew lease as it was not held anymore. eTag is null");
    }
    return atomicAcquireLease(currentLease, true);
  }

  public Lease getLease(final int id) throws IOException {
    final var response =
        client
            .getObject(
                h -> h.bucket(bucketName).key(String.valueOf(id)),
                AsyncResponseTransformer.toBytes())
            .join();
    return Lease.fromJsonBytes(OBJECT_MAPPER, response.asByteArray());
  }

  public static String objectKey(final int nodeId) {
    return nodeId + ".json";
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
