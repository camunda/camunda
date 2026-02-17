/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository.s3;

import static io.camunda.zeebe.dynamic.nodeid.Lease.OBJECT_MAPPER;

import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.StoredRestoreStatus;
import io.camunda.zeebe.dynamic.nodeid.StoredRestoreStatus.RestoreStatus;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3NodeIdRepository implements NodeIdRepository {

  private static final Logger LOG = LoggerFactory.getLogger(S3NodeIdRepository.class);
  private static final String NODEID_INITIALIZATION_MARKER_KEY = "node-ids-initialized";
  private static final String NODE_ID_KEY_PATTERN = "\\d+\\.json";
  private final S3Client client;
  private final Config config;
  private final InstantSource clock;
  private final boolean closeClient;

  public S3NodeIdRepository(
      final S3Client s3Client,
      final Config config,
      final InstantSource clock,
      final boolean closeClient) {
    client = s3Client;
    this.config = config;
    this.clock = clock;
    this.closeClient = closeClient;
  }

  public static S3NodeIdRepository of(
      final S3ClientConfig s3ClientConfig, final Config config, final Clock clock) {
    final var client = buildClient(s3ClientConfig);
    // the client must be closed if it's built here
    return new S3NodeIdRepository(client, config, clock, true);
  }

  @Override
  public int initialize(final int initialCount) {
    final var initialized = isInitialized();
    if (!initialized) {
      if (initialCount <= 0) {
        throw new IllegalArgumentException("initialCount must be greater than 0");
      }

      IntStream.range(0, initialCount).forEach(this::initializeForNode);

      // We need a marker object to mark the completion of initialization.
      // In case the initialization fails halfway, marker file does not exist and the retries will
      // complete the initialization.
      markInitialized();
    }
    return getAvailableLeaseCount();
  }

  @Override
  public void scale(final int newCount) {
    if (newCount <= 0) {
      throw new IllegalArgumentException("newCount must be greater than 0");
    }
    // Keep it simple for now by just creating new leases for new nodes and deleting leases for
    // removed nodes. We can improve it later by making the leases non-acquirable. This would let
    // graceful shutdown of tasks that are still holding those leases.
    final var oldCount = getAvailableLeaseCount();
    if (newCount > oldCount) {
      // scale up => add new leases
      IntStream.range(oldCount, newCount).forEach(this::initializeForNode);
    } else {
      // scale down =>  delete extra leases
      // Delete in reverse order so that retry after failure will correctly delete remaining node
      // ids
      for (int nodeId = oldCount - 1; nodeId >= newCount; nodeId--) {
        final var request =
            DeleteObjectRequest.builder().bucket(config.bucketName).key(objectKey(nodeId)).build();
        try {
          client.deleteObject(request);
        } catch (final S3Exception e) {
          if (e.statusCode() == 404) {
            LOG.debug("Lease for nodeId {} does not exist, likely already deleted.", nodeId);
          } else {
            LOG.warn("Failed to delete lease object for node {} ", nodeId, e);
            throw e;
          }
        }
      }
    }
  }

  @Override
  public StoredLease getLease(final int nodeId) {
    final var request =
        GetObjectRequest.builder().bucket(config.bucketName).key(objectKey(nodeId)).build();
    final var response = client.getObject(request);
    try {
      final var bytes = response.readAllBytes();
      final var lease = bytes.length > 0 ? Lease.fromJsonBytes(OBJECT_MAPPER, bytes) : null;
      final var metadata = Metadata.fromMap(response.response().metadata());
      final var eTag = response.response().eTag();
      final var storedLease = StoredLease.of(nodeId, lease, metadata, eTag);
      LOG.trace("Lease for object {} is {}", nodeId, storedLease);
      return storedLease;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public StoredLease.Initialized acquire(final Lease lease, final String previousETag) {
    if (lease == null) {
      throw new IllegalArgumentException("lease is null");
    }
    final var nodeId = lease.nodeInstance().id();
    final var metadata = Metadata.fromLease(lease);
    final PutObjectRequest putRequest =
        createPutObjectRequest(nodeId, Optional.of(metadata), Optional.of(previousETag)).build();
    try {
      if (!lease.isStillValid(clock.millis())) {
        throw new IllegalArgumentException("The provided lease is not valid anymore: " + lease);
      }
      LOG.debug("Acquiring lease {}, previous ETag {}", lease, previousETag);
      final var response =
          client.putObject(putRequest, RequestBody.fromBytes(lease.toJsonBytes(OBJECT_MAPPER)));
      final var storedLease = new StoredLease.Initialized(metadata, lease, response.eTag());
      LOG.debug("Lease acquired successfully {}", storedLease);
      return storedLease;
    } catch (final Exception e) {
      LOG.warn("Failed to acquire the lease {}", lease, e);
      throw e;
    }
  }

  @Override
  public void release(final StoredLease.Initialized lease) {
    final var nodeId = lease.lease().nodeInstance().id();
    final PutObjectRequest putRequest =
        createPutObjectRequest(
                nodeId, Optional.of(lease.metadata().forRelease()), Optional.of(lease.eTag()))
            .build();
    try {
      LOG.info("Release lease {}", lease);
      client.putObject(putRequest, RequestBody.empty());
      LOG.debug("Lease released gracefully: {}", lease);
    } catch (final Exception e) {
      LOG.warn("Failed to release the lease gracefully {}", lease, e);
    }
  }

  @Override
  public void updateRestoreStatus(final RestoreStatus restoreStatus, final String etag) {
    final PutObjectRequest.Builder putRequestBuilder =
        PutObjectRequest.builder()
            .bucket(config.bucketName)
            .key(getRestoreStatusKey(restoreStatus.restoreId()))
            .contentType("application/json");

    if (etag == null || etag.isEmpty()) {
      putRequestBuilder.ifNoneMatch("*");
    } else {
      putRequestBuilder.ifMatch(etag);
    }

    final var putRequest = putRequestBuilder.build();

    try {
      LOG.debug("Marking restore status as {}", restoreStatus);
      final var response =
          client.putObject(
              putRequest, RequestBody.fromBytes(restoreStatus.toJsonBytes(OBJECT_MAPPER)));
      LOG.debug("Restore status marked successfully with ETag {}", response.eTag());
    } catch (final Exception e) {
      LOG.warn("Failed to mark restore status {}", restoreStatus, e);
      throw e;
    }
  }

  @Override
  public StoredRestoreStatus getRestoreStatus(final String restoreId) {
    final var request =
        GetObjectRequest.builder()
            .bucket(config.bucketName)
            .key(getRestoreStatusKey(restoreId))
            .build();
    try {
      final var response = client.getObject(request);
      final var bytes = response.readAllBytes();
      if (bytes.length > 0) {
        final var restoreStatus = RestoreStatus.fromJsonBytes(OBJECT_MAPPER, bytes);
        LOG.trace("Restore status is {}", restoreStatus);
        return new StoredRestoreStatus(restoreStatus, response.response().eTag());
      }
      return null;
    } catch (final S3Exception e) {
      if (e.statusCode() == 404) {
        LOG.debug("Restore status object not found");
        return null;
      }
      LOG.warn("Failed to get restore status", e);
      throw e;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void markInitialized() {
    // concurrent puts to these object is fine as the content is not relevant.
    final var request =
        PutObjectRequest.builder()
            .bucket(config.bucketName)
            .key(NODEID_INITIALIZATION_MARKER_KEY)
            .build();
    try {
      client.putObject(request, RequestBody.empty());
      LOG.debug("Node ID repository initialized successfully");
    } catch (final Exception e) {
      LOG.warn("Failed to mark node ID repository as initialized", e);
      throw e;
    }
  }

  // returns true if the marker object exists, false if it doesn't exist, and throws exception if
  // the check failed for other reasons (e.g. permissions, network error, etc.)
  private boolean isInitialized() {
    final var request =
        GetObjectRequest.builder()
            .bucket(config.bucketName)
            .key(NODEID_INITIALIZATION_MARKER_KEY)
            .build();
    try {
      client.getObject(request);
      return true;
    } catch (final S3Exception e) {
      if (e.statusCode() == 404) {
        LOG.debug("Node ID repository is not initialized yet");
        return false;
      }
      LOG.warn("Failed to check if node ID repository is initialized", e);
      throw e;
    }
  }

  private int getAvailableLeaseCount() {
    final var request = ListObjectsV2Request.builder().bucket(config.bucketName).build();
    try {
      final var response = client.listObjectsV2(request);
      if (response.hasContents() && !response.contents().isEmpty()) {
        return (int) response.contents().stream().filter(o -> isNodeIdLease(o.key())).count();
      }
      return 0;
    } catch (final S3Exception e) {
      if (e.statusCode() == 404) {
        LOG.warn("Bucket {} does not exist", config.bucketName);
        throw e;
      }
      LOG.warn("Failed to list objects in bucket {}", config.bucketName, e);
      throw e;
    }
  }

  private boolean isNodeIdLease(final String key) {
    // key is of pattern "<nodeId>.json"
    return key.matches(NODE_ID_KEY_PATTERN);
  }

  private String getRestoreStatusKey(final String restoreId) {
    return "restore/%s".formatted(restoreId);
  }

  public void initializeForNode(final int nodeId) {
    final PutObjectRequest putRequest =
        createPutObjectRequest(nodeId, Optional.empty(), Optional.empty()).ifNoneMatch("*").build();
    try {
      LOG.debug("Creating file for node {}", nodeId);
      final var res = client.putObject(putRequest, RequestBody.empty());
      LOG.debug("File created for nodeId {}: {}", nodeId, res.eTag());
    } catch (final Exception e) {
      if (e instanceof final S3Exception s3Exception) {
        // if bucket does not exist, it return 404
        if (s3Exception.statusCode() == 404) {
          throw new IllegalArgumentException(
              "Cannot create file for node " + nodeId + " in bucket " + config.bucketName, e);
        } else if (s3Exception.statusCode() == 412) {
          // Precondition failed is returned when the object already exists, which can happen in
          // case of multiple nodes starting at the same time, so we can ignore it here.
          LOG.debug(
              "File for nodeId {} already exists, likely created by another node starting at the same time.",
              nodeId);
          return;
        }
      }
      LOG.debug(
          "File creation failed for nodeId {}: {}",
          nodeId,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      // Need to fail so that initialization is retried.
      throw e;
    }
  }

  private PutObjectRequest.Builder createPutObjectRequest(
      final int nodeId, final Optional<Metadata> metadata, final Optional<String> eTag) {
    final var builder =
        PutObjectRequest.builder()
            .bucket(config.bucketName)
            .key(objectKey(nodeId))
            .contentType("application/json");
    metadata.ifPresent(value -> builder.metadata(value.asMap()));
    eTag.ifPresent(builder::ifMatch);
    return builder;
  }

  @Override
  public void close() throws Exception {
    if (closeClient) {
      client.close();
    }
  }

  public static String objectKey(final int nodeId) {
    return nodeId + ".json";
  }

  public static S3Client buildClient(final S3ClientConfig config) {
    final var builder = S3Client.builder();

    config.region.ifPresent(builder::region);
    config.credentials.ifPresent(
        credentials ->
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credentials.accessKey(), credentials.secretKey()))));

    builder.defaultsMode(config.defaultsMode.orElse(DefaultsMode.AUTO));
    builder.httpClientBuilder(
        ApacheHttpClient.builder()
            .tcpKeepAlive(true)
            .connectionAcquisitionTimeout(
                config.connectionAcquisitionTimeout.orElse(Duration.ofSeconds(5))));
    config.endpoint.ifPresent(builder::endpointOverride);
    builder.overrideConfiguration(
        cfg -> cfg.retryStrategy(config.retryStrategy.orElse(RetryMode.ADAPTIVE_V2)));
    return builder.build();
  }

  public record Config(
      String bucketName, Duration leaseDuration, Duration nodeIdMappingUpdateTimeout) {
    public Config {
      if (leaseDuration.toMillis() <= 0) {
        throw new IllegalArgumentException("leaseDuration must be greater than 0");
      }
      if (bucketName == null || bucketName.isEmpty()) {
        throw new IllegalArgumentException("bucketName cannot be null or empty");
      }
      if (nodeIdMappingUpdateTimeout.toMillis() <= 0) {
        throw new IllegalArgumentException("nodeIdMappingUpdateTimeout must be greater than 0");
      }
      if (nodeIdMappingUpdateTimeout.toMillis() <= leaseDuration.toMillis()) {
        throw new IllegalArgumentException(
            "Expected nodeIdMappingUpdateTimeout to be greater than leaseDuration (%s), but got %s."
                .formatted(leaseDuration, nodeIdMappingUpdateTimeout));
      }
    }
  }

  public record S3ClientConfig(
      Optional<Credentials> credentials,
      Optional<Region> region,
      Optional<URI> endpoint,
      Optional<RetryMode> retryStrategy,
      Optional<DefaultsMode> defaultsMode,
      Optional<Duration> connectionAcquisitionTimeout,
      Optional<Duration> apiCallTimeout) {

    public S3ClientConfig(
        final Optional<Credentials> credentials,
        final Optional<Region> region,
        final Optional<URI> endpoint) {
      this(
          credentials,
          region,
          endpoint,
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    public S3ClientConfig {
      if (region.isEmpty()) {
        LOG.warn(
            "region is not configured, Credentials will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)");
      }
    }

    public record Credentials(String accessKey, String secretKey) {
      public Credentials {
        if (accessKey == null || accessKey.isEmpty()) {
          LOG.warn(
              "accessKey is not configured, Credentials will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)");
        }
        if (secretKey == null || secretKey.isEmpty()) {
          LOG.warn(
              "secretKey is not configured, Credentials will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)");
        }
      }
    }
  }
}
