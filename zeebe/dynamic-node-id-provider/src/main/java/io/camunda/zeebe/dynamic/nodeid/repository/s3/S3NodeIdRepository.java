/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dynamic.nodeid.Lease;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3NodeIdRepository implements NodeIdRepository {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Logger LOG = LoggerFactory.getLogger(S3NodeIdRepository.class);
  private final S3Client client;
  private final Config config;
  private final InstantSource clock;
  private final boolean closeClient;

  public S3NodeIdRepository(
      final S3Client s3AsyncClient,
      final Config config,
      final InstantSource clock,
      final boolean closeClient) {
    client = s3AsyncClient;
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
  public void initialize(final int count) {
    IntStream.range(0, count).forEach(this::initializeForNode);
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
    final var nodeId = lease.nodeInstance().id();
    final var metadata = Metadata.fromLease(lease);
    final PutObjectRequest putRequest =
        createPutObjectRequest(nodeId, Optional.of(metadata), Optional.of(previousETag)).build();
    try {
      if (!lease.isStillValid(clock.millis(), config.expiryDuration)) {
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
        createPutObjectRequest(nodeId, Optional.empty(), Optional.of(lease.eTag())).build();
    try {
      LOG.info("Release lease {}", lease);
      client.putObject(putRequest, RequestBody.empty());
      LOG.debug("Lease released gracefully: {}", lease);
    } catch (final Exception e) {
      LOG.warn("Failed to release the lease gracefully {}", lease, e);
    }
  }

  public void initializeForNode(final int nodeId) {
    final PutObjectRequest putRequest =
        createPutObjectRequest(nodeId, Optional.empty(), Optional.empty()).ifNoneMatch("*").build();
    try {
      LOG.debug("Creating file for node {}", nodeId);
      final var res = client.putObject(putRequest, RequestBody.empty());
      LOG.debug("File created for nodeId {}: {}", nodeId, res.eTag());
    } catch (final Exception e) {
      LOG.debug(
          "File creation failed for nodeId {}: {}",
          nodeId,
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
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

  public record Config(String bucketName, Duration expiryDuration) {
    public Config {
      if (expiryDuration.toMillis() <= 0) {
        throw new IllegalArgumentException("expiryDuration must be greater than 0");
      }
      if (bucketName == null || bucketName.isEmpty()) {
        throw new IllegalArgumentException("bucketName cannot be null or empty");
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
