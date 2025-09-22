/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class S3 {

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

  public void setCompression(final String compression) {
    this.compression = compression;
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

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }
}
