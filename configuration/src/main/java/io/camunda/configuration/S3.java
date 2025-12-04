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
import java.util.LinkedHashSet;
import java.util.Set;

public class S3 {
  private static final String PREFIX = "camunda.data.primary-storage.backup.s3";

  private static final Set<Set<String>> LEGACY_BUCKETNAME_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_ENDPOINT_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_REGION_PROPERTIES = new LinkedHashSet<>(2);
  private static final Set<Set<String>> LEGACY_ACCESS_KEY_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_SECRET_KEY_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_API_CALL_TIMEOUT_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_FORCE_PATH_STYLE_ACCESS_PROPERTIES =
      new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_COMPRESSION_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_MAX_CONCURRENT_CONNECTIONS_PROPERTIES =
      new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_CONNECTION_AQUISITION_TIMEOUT_PROPERTIES =
      new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_SUPPORT_LEGACY_MD5_PROPERTIES =
      new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_BASEPATH_PROPERTIES = new LinkedHashSet<>(2);

  static {
    LEGACY_BUCKETNAME_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.bucketName"));
    LEGACY_BUCKETNAME_PROPERTIES.add(Set.of("camunda.data.backup.s3.bucket-name"));

    LEGACY_ENDPOINT_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.endpoint"));
    LEGACY_ENDPOINT_PROPERTIES.add(Set.of("camunda.data.backup.s3.endpoint"));

    LEGACY_REGION_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.region"));
    LEGACY_REGION_PROPERTIES.add(Set.of("camunda.data.backup.s3.region"));

    LEGACY_ACCESS_KEY_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.accessKey"));
    LEGACY_ACCESS_KEY_PROPERTIES.add(Set.of("camunda.data.backup.s3.access-key"));

    LEGACY_SECRET_KEY_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.secretKey"));
    LEGACY_SECRET_KEY_PROPERTIES.add(Set.of("camunda.data.backup.s3.secret-key"));

    LEGACY_API_CALL_TIMEOUT_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.apiCallTimeout"));
    LEGACY_API_CALL_TIMEOUT_PROPERTIES.add(Set.of("camunda.data.backup.s3.api-call-timeout"));

    LEGACY_FORCE_PATH_STYLE_ACCESS_PROPERTIES.add(
        Set.of("zeebe.broker.data.backup.s3.forcePathStyleAccess"));
    LEGACY_FORCE_PATH_STYLE_ACCESS_PROPERTIES.add(
        Set.of("camunda.data.backup.s3.force-path-style-access"));

    LEGACY_COMPRESSION_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.compression"));
    LEGACY_COMPRESSION_PROPERTIES.add(Set.of("camunda.data.backup.s3.compression"));

    LEGACY_MAX_CONCURRENT_CONNECTIONS_PROPERTIES.add(
        Set.of("zeebe.broker.data.backup.s3.maxConcurrentConnections"));
    LEGACY_MAX_CONCURRENT_CONNECTIONS_PROPERTIES.add(
        Set.of("camunda.data.backup.s3.max-concurrent-connections"));

    LEGACY_CONNECTION_AQUISITION_TIMEOUT_PROPERTIES.add(
        Set.of("zeebe.broker.data.backup.s3.connectionAcquisitionTimeout"));
    LEGACY_CONNECTION_AQUISITION_TIMEOUT_PROPERTIES.add(
        Set.of("camunda.data.backup.s3.connection-acquisition-timeout"));

    LEGACY_SUPPORT_LEGACY_MD5_PROPERTIES.add(
        Set.of("zeebe.broker.data.backup.s3.supportLegacyMd5"));
    LEGACY_SUPPORT_LEGACY_MD5_PROPERTIES.add(Set.of("camunda.data.backup.s3.support-legacy-md5"));

    LEGACY_BASEPATH_PROPERTIES.add(Set.of("zeebe.broker.data.backup.s3.basePath"));
    LEGACY_BASEPATH_PROPERTIES.add(Set.of("camunda.data.backup.s3.base-path"));
  }

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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".support-legacy-md5",
        supportLegacyMd5,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SUPPORT_LEGACY_MD5_PROPERTIES);
  }

  public void setSupportLegacyMd5(final boolean supportLegacyMd5) {
    this.supportLegacyMd5 = supportLegacyMd5;
  }

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
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
