/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;
import java.util.Objects;

public class S3BackupStoreConfig implements ConfigurationEntry {

  private String bucketName;
  private String endpoint;
  private String region;
  private String accessKey;
  private String secretKey;
  private Duration apiCallTimeout = Duration.ofSeconds(180);
  private boolean forcePathStyleAccess = false;
  private String compression;
  private boolean supportLegacyMd5;

  /** Default from `SdkHttpConfigurationOption.MAX_CONNECTIONS` */
  private int maxConcurrentConnections = 50;

  /** Default from `SdkHttpConfigurationOption.DEFAULT_CONNECTION_ACQUIRE_TIMEOUT` */
  private Duration connectionAcquisitionTimeout = Duration.ofSeconds(45);

  private String basePath;

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(final String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(final String secretKey) {
    this.secretKey = secretKey;
  }

  public Duration getApiCallTimeout() {
    return apiCallTimeout;
  }

  public void setApiCallTimeout(final Duration apiCallTimeout) {
    this.apiCallTimeout = apiCallTimeout;
  }

  public boolean isForcePathStyleAccess() {
    return forcePathStyleAccess;
  }

  public void setForcePathStyleAccess(final boolean forcePathStyleAccess) {
    this.forcePathStyleAccess = forcePathStyleAccess;
  }

  public String getCompression() {
    return compression;
  }

  public void setCompression(final String algorithm) {
    if (Objects.equals(algorithm, "none")) {
      compression = null;
    } else {
      compression = algorithm;
    }
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public int getMaxConcurrentConnections() {
    return maxConcurrentConnections;
  }

  public void setMaxConcurrentConnections(final int maxConcurrentConnections) {
    this.maxConcurrentConnections = maxConcurrentConnections;
  }

  public Duration getConnectionAcquisitionTimeout() {
    return connectionAcquisitionTimeout;
  }

  public void setConnectionAcquisitionTimeout(final Duration connectionAcquisitionTimeout) {
    this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
  }

  public boolean isSupportLegacyMd5() {
    return supportLegacyMd5;
  }

  public void setSupportLegacyMd5(final boolean supportLegacyMd5) {
    this.supportLegacyMd5 = supportLegacyMd5;
  }

  public static S3BackupConfig toStoreConfig(final S3BackupStoreConfig config) {
    final var builder =
        new Builder()
            .withBucketName(config.getBucketName())
            .withEndpoint(config.getEndpoint())
            .withRegion(config.getRegion())
            .withApiCallTimeout(config.getApiCallTimeout())
            .forcePathStyleAccess(config.isForcePathStyleAccess())
            .withCompressionAlgorithm(config.getCompression())
            .withBasePath(config.getBasePath())
            .withMaxConcurrentConnections(config.getMaxConcurrentConnections())
            .withConnectionAcquisitionTimeout(config.getConnectionAcquisitionTimeout())
            .withSupportLegacyMd5(config.isSupportLegacyMd5());
    if (config.getAccessKey() != null && config.getSecretKey() != null) {
      builder.withCredentials(config.getAccessKey(), config.getSecretKey());
    }
    return builder.build();
  }

  @Override
  public int hashCode() {
    int result = bucketName != null ? bucketName.hashCode() : 0;
    result = 31 * result + (endpoint != null ? endpoint.hashCode() : 0);
    result = 31 * result + (region != null ? region.hashCode() : 0);
    result = 31 * result + (accessKey != null ? accessKey.hashCode() : 0);
    result = 31 * result + (secretKey != null ? secretKey.hashCode() : 0);
    result = 31 * result + (apiCallTimeout != null ? apiCallTimeout.hashCode() : 0);
    result = 31 * result + (forcePathStyleAccess ? 1 : 0);
    result = 31 * result + (compression != null ? compression.hashCode() : 0);
    result = 31 * result + (basePath != null ? basePath.hashCode() : 0);
    result = 31 * result + maxConcurrentConnections;
    result =
        31 * result
            + (connectionAcquisitionTimeout != null ? connectionAcquisitionTimeout.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final S3BackupStoreConfig that = (S3BackupStoreConfig) o;

    return forcePathStyleAccess == that.forcePathStyleAccess
        && maxConcurrentConnections == that.maxConcurrentConnections
        && Objects.equals(bucketName, that.bucketName)
        && Objects.equals(endpoint, that.endpoint)
        && Objects.equals(region, that.region)
        && Objects.equals(accessKey, that.accessKey)
        && Objects.equals(secretKey, that.secretKey)
        && Objects.equals(apiCallTimeout, that.apiCallTimeout)
        && Objects.equals(compression, that.compression)
        && Objects.equals(basePath, that.basePath)
        && Objects.equals(connectionAcquisitionTimeout, that.connectionAcquisitionTimeout)
        && supportLegacyMd5 == that.supportLegacyMd5;
  }

  @Override
  public String toString() {
    return "S3BackupStoreConfig{"
        + "bucketName='"
        + bucketName
        + '\''
        + ", endpoint='"
        + endpoint
        + '\''
        + ", region='"
        + region
        + '\''
        + ", accessKey='"
        + accessKey
        + '\''
        + ", secretKey='"
        + "<redacted>"
        + '\''
        + ", apiCallTimeout="
        + apiCallTimeout
        + ", forcePathStyleAccess="
        + forcePathStyleAccess
        + ", compression="
        + compression
        + ", basePath="
        + basePath
        + ", maxConcurrentConnections="
        + maxConcurrentConnections
        + ", connectionAcquisitionTimeout="
        + connectionAcquisitionTimeout
        + ", supportLegacyMd5="
        + supportLegacyMd5
        + '}';
  }
}
