/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.google.auth.Credentials;
import java.time.Duration;
import java.util.Optional;

public record AzureBackupConfig(
    String bucketName,
    Optional<String> endpoint,
    Optional<String> region,
    Optional<Credentials> credentials,
    Optional<Duration> apiCallTimeout,
    boolean forcePathStyleAccess,
    Optional<String> compressionAlgorithm,
    Optional<String> basePath,
    Integer maxConcurrentConnections,
    Duration connectionAcquisitionTimeout) {

  public static class Builder {

    private String bucketName;
    private String endpoint;
    private String region;
    private Duration apiCallTimeoutMs;
    private boolean forcePathStyleAccess = false;
    private String compressionAlgorithm;
    private Credentials credentials;
    private String basePath;

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
      //      credentials = new Credentials(accessKey, secretKey);
      credentials = null;
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

    public Builder withParallelUploadsLimit(final Integer parallelUploadsLimit) {
      maxConcurrentConnections = parallelUploadsLimit;
      return this;
    }

    public Builder withConnectionAcquisitionTimeout(final Duration connectionAcquisitionTimeout) {
      this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
      return this;
    }

    public AzureBackupConfig build() {
      return new AzureBackupConfig(
          bucketName,
          Optional.ofNullable(endpoint),
          Optional.ofNullable(region),
          Optional.ofNullable(credentials),
          Optional.ofNullable(apiCallTimeoutMs),
          forcePathStyleAccess,
          Optional.ofNullable(compressionAlgorithm),
          Optional.ofNullable(basePath),
          maxConcurrentConnections,
          connectionAcquisitionTimeout);
    }
  }
}
