/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * Holds configuration for the {@link S3BackupStore S3 Backup Store}.
 *
 * @param bucketName Name of the bucket that will be used for storing backups.
 * @param endpoint URL for the S3 endpoint to connect to.
 * @param region Name of the S3 region to use, will be parsed by {@link
 *     software.amazon.awssdk.regions.Region#of(String)}. If no value is provided, the AWS SDK will
 *     try to discover an appropriate value from the environment.
 * @param credentials If no value is provided, the AWS SDK will try to discover appropriate values
 *     from the environment.
 * @param apiCallTimeout Used as the overall api call timeout for the AWS SDK. API calls that exceed
 *     the timeout may fail and result in failed backups.
 * @param forcePathStyleAccess Forces the AWS SDK to always use paths for accessing the bucket. Off
 *     by default, which allows the AWS SDK to choose virtual-hosted-style bucket access.
 * @param compressionAlgorithm Algorithm to use (if any) for compressing backup contents.
 * @param basePath Prefix to use for all objects in this bucket. Must be non-empty and not start or
 *     end with '/'.
 * @param maxConcurrentConnections Maximum number of connections allowed in a connection pool.
 * @param connectionAcquisitionTimeout Timeout for acquiring an already-established connection from
 *     a connection pool to a remote service.
 * @see <a
 *     href=https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment>
 *     Automatically determine the Region from the environment</a>
 * @see software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
 * @see <a
 *     href=https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html>Differences
 *     between path-style and virtual-hosted-style access</a>
 */
public record S3BackupConfig(
    String bucketName,
    Optional<String> endpoint,
    Optional<String> region,
    Optional<Credentials> credentials,
    Optional<Duration> apiCallTimeout,
    boolean forcePathStyleAccess,
    Optional<String> compressionAlgorithm,
    Optional<String> basePath,
    Integer maxConcurrentConnections,
    Duration connectionAcquisitionTimeout,
    boolean supportLegacyMd5) {

  public S3BackupConfig {
    if (bucketName == null || bucketName.isEmpty()) {
      throw new IllegalArgumentException("Bucket name must not be empty.");
    }
    if (compressionAlgorithm.isPresent()) {
      final var inputAlgorithms =
          CompressorStreamFactory.getSingleton().getInputStreamCompressorNames();
      final var outputAlgorithms =
          CompressorStreamFactory.getSingleton().getOutputStreamCompressorNames();
      final var supported = new HashSet<>(inputAlgorithms);
      supported.retainAll(outputAlgorithms);

      if (!supported.contains(compressionAlgorithm.get())) {
        throw new IllegalArgumentException(
            "Can't use compression algorithm %s. Only supports %s"
                .formatted(compressionAlgorithm.get(), supported));
      }
    }
    if (basePath.isPresent()) {
      final var prefix = basePath.get();
      if (prefix.isEmpty()) {
        throw new IllegalArgumentException(
            "basePath is set but empty. It must be either unset or not empty.");
      }
      if (prefix.startsWith("/") || prefix.endsWith("/")) {
        throw new IllegalArgumentException(
            "basePath must not start or end with '/' but was: %s".formatted(prefix));
      }
    }
  }

  public static class Builder {

    private String bucketName;
    private String endpoint;
    private String region;
    private Duration apiCallTimeoutMs;
    private boolean forcePathStyleAccess = false;
    private String compressionAlgorithm;
    private Credentials credentials;
    private String basePath;
    private boolean supportLegacyMd5 = false;

    /** Default from `SdkHttpConfigurationOption.MAX_CONNECTIONS` */
    private Integer maxConcurrentConnections = 50;

    /** Default from `SdkHttpConfigurationOption.DEFAULT_CONNECTION_ACQUIRE_TIMEOUT` */
    private Duration connectionAcquisitionTimeout = Duration.ofSeconds(45);

    public Builder withBucketName(final String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder withEndpoint(final String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public Builder withRegion(final String region) {
      this.region = region;
      return this;
    }

    public Builder withCredentials(final String accessKey, final String secretKey) {
      credentials = new Credentials(accessKey, secretKey);
      return this;
    }

    public Builder withApiCallTimeout(final Duration apiCallTimeoutMs) {
      this.apiCallTimeoutMs = apiCallTimeoutMs;
      return this;
    }

    public Builder forcePathStyleAccess(final boolean forcePathStyleAccess) {
      this.forcePathStyleAccess = forcePathStyleAccess;
      return this;
    }

    public Builder withCompressionAlgorithm(final String compressionAlgorithm) {
      this.compressionAlgorithm = compressionAlgorithm;
      return this;
    }

    public Builder withBasePath(final String basePath) {
      this.basePath = basePath;
      return this;
    }

    public Builder withMaxConcurrentConnections(final Integer maxConcurrentConnections) {
      this.maxConcurrentConnections = maxConcurrentConnections;
      return this;
    }

    public Builder withConnectionAcquisitionTimeout(final Duration connectionAcquisitionTimeout) {
      this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
      return this;
    }

    public Builder withSupportLegacyMd5(final boolean useMd5LegacyPlugin) {
      supportLegacyMd5 = useMd5LegacyPlugin;
      return this;
    }

    public S3BackupConfig build() {
      return new S3BackupConfig(
          bucketName,
          Optional.ofNullable(endpoint),
          Optional.ofNullable(region),
          Optional.ofNullable(credentials),
          Optional.ofNullable(apiCallTimeoutMs),
          forcePathStyleAccess,
          Optional.ofNullable(compressionAlgorithm),
          Optional.ofNullable(basePath),
          maxConcurrentConnections,
          connectionAcquisitionTimeout,
          supportLegacyMd5);
    }
  }

  record Credentials(String accessKey, String secretKey) {
    @Override
    public String toString() {
      return "Credentials{"
          + "accessKey='"
          + accessKey
          + '\''
          + ", secretKey='"
          + "<redacted>"
          + '\''
          + '}';
    }
  }
}
