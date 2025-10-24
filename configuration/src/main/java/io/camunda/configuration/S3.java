/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class S3 {
  private static final String PREFIX = "camunda.data.backup.s3";
  private static final Set<String> LEGACY_BUCKETNAME_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.bucketName");
  private static final Set<String> LEGACY_ENDPOINT_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.endpoint");
  private static final Set<String> LEGACY_REGION_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.region");
  private static final Set<String> LEGACY_ACCESS_KEY_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.accessKey");
  private static final Set<String> LEGACY_SECRET_KEY_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.secretKey");
  private static final Set<String> LEGACY_API_CALL_TIMEOUT_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.apiCallTimeout");
  private static final Set<String> LEGACY_FORCE_PATH_STYLE_ACCESS_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.forcePathStyleAccess");
  private static final Set<String> LEGACY_COMPRESSION_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.compression");
  private static final Set<String> LEGACY_MAX_CONCURRENT_CONNECTIONS_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.maxConcurrentConnections");
  private static final Set<String> LEGACY_CONNECTION_AQUISITION_TIMEOUT_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.connectionAcquisitionTimeout");
  private static final Set<String> LEGACY_SUPPPORT_LEGACY_MD5_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.supportLegacyMd5");
  private static final Set<String> LEGACY_BASEPATH_PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.basePath");

  /**
   * Name of the bucket where the backup will be stored. The bucket must be already created. The
   * bucket must not be shared with other zeebe clusters. bucketName must not be empty.
   */
  private String bucketName;

  /**
   * Configure URL endpoint for the store. If no endpoint is provided, it will be determined based
   * on the configured region.
   */
  private String endpoint;

  /**
   * Configure AWS region. If no region is provided it will be determined as documented in <a
   * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment">...</a>
   */
  private String region;

  /**
   * Configure access credentials. If either accessKey or secretKey is not provided, the credentials
   * will be determined as documented in <a
   * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
   */
  private String accessKey;

  /**
   * Configure access credentials. If either accessKey or secretKey is not provided, the credentials
   * will be determined as documented in <a
   * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
   */
  private String secretKey;

  /**
   * Configure a maximum duration for all S3 client API calls. Lower values will ensure that failed
   * or slow API calls don't block other backups but may increase the risk that backups can't be
   * stored if uploading parts of the backup takes longer than the configured timeout. See <a
   * href="https://github.com/aws/aws-sdk-java-v2/blob/master/docs/BestPractices.md#utilize-timeout-configurations">...</a>
   */
  private Duration apiCallTimeout = Duration.ofSeconds(180);

  /**
   * When enabled, forces the s3 client to use path-style access. By default, the client will
   * automatically choose between path-style and virtual-hosted-style. Should only be enabled if the
   * s3 compatible storage cannot support virtual-hosted-style. See <a
   * href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html">...</a>
   */
  private boolean forcePathStyleAccess = false;

  /**
   * When set to an algorithm such as 'zstd', enables compression of backup contents. When not set
   * or set to 'none', backup content is not compressed. Enabling compression reduces the required
   * storage space for backups in S3 but also increases the impact on CPU and disk utilization while
   * taking a backup.
   */
  private String compression;

  /** Enable s3 md5 plugin for legacy support */
  private boolean supportLegacyMd5;

  /**
   * Maximum number of connections allowed in a connection pool. This is used to restrict the
   * maximum number of concurrent uploads as to avoid connection timeouts when uploading backups
   * with large/many files.
   */
  private int maxConcurrentConnections = 50;

  /**
   * Timeout for acquiring an already-established connection from a connection pool to a remote
   * service.
   */
  private Duration connectionAcquisitionTimeout = Duration.ofSeconds(45);

  /**
   * When set, all objects in the bucket will use this prefix. Must be non-empty and not start or
   * end with '/'. Useful for using the same bucket for multiple Zeebe clusters. In this case,
   * basePath must be unique.
   */
  private String basePath;

  public String getBucketName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".bucket-name",
        bucketName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BUCKETNAME_PROPERTIES);
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public String getEndpoint() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".endpoint",
        endpoint,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENDPOINT_PROPERTIES);
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".region",
        region,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_REGION_PROPERTIES);
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public String getAccessKey() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".access-key",
        accessKey,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCESS_KEY_PROPERTIES);
  }

  public void setAccessKey(final String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".secret-key",
        secretKey,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SECRET_KEY_PROPERTIES);
  }

  public void setSecretKey(final String secretKey) {
    this.secretKey = secretKey;
  }

  public Duration getApiCallTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".api-call-timeout",
        apiCallTimeout,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_API_CALL_TIMEOUT_PROPERTIES);
  }

  public void setApiCallTimeout(final Duration apiCallTimeout) {
    this.apiCallTimeout = apiCallTimeout;
  }

  public boolean isForcePathStyleAccess() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".force-path-style-access",
        forcePathStyleAccess,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FORCE_PATH_STYLE_ACCESS_PROPERTIES);
  }

  public void setForcePathStyleAccess(final boolean forcePathStyleAccess) {
    this.forcePathStyleAccess = forcePathStyleAccess;
  }

  public String getCompression() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".compression",
        compression,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_COMPRESSION_PROPERTIES);
  }

  public void setCompression(final String compression) {
    this.compression = compression;
  }

  public int getMaxConcurrentConnections() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-concurrent-connections",
        maxConcurrentConnections,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_CONCURRENT_CONNECTIONS_PROPERTIES);
  }

  public void setMaxConcurrentConnections(final int maxConcurrentConnections) {
    this.maxConcurrentConnections = maxConcurrentConnections;
  }

  public Duration getConnectionAcquisitionTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".connection-acquisition-timeout",
        connectionAcquisitionTimeout,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CONNECTION_AQUISITION_TIMEOUT_PROPERTIES);
  }

  public void setConnectionAcquisitionTimeout(final Duration connectionAcquisitionTimeout) {
    this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
  }

  public boolean isSupportLegacyMd5() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".support-legacy-md5",
        supportLegacyMd5,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SUPPPORT_LEGACY_MD5_PROPERTIES);
  }

  public void setSupportLegacyMd5(final boolean supportLegacyMd5) {
    this.supportLegacyMd5 = supportLegacyMd5;
  }

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".base-path",
        basePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BASEPATH_PROPERTIES);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }
}
