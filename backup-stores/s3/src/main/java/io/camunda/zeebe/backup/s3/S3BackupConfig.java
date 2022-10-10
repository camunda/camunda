/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import java.time.Duration;
import java.util.Optional;

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
 * @see <a
 *     href=https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment>
 *     Automatically determine the Region from the environment</a>
 * @see software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
 */
public record S3BackupConfig(
    String bucketName,
    Optional<String> endpoint,
    Optional<String> region,
    Optional<Credentials> credentials,
    Optional<Duration> apiCallTimeout) {

  /**
   * Creates a config without setting the region and credentials.
   *
   * @param bucketName Name of the backup that will be used for storing backups
   * @see S3BackupConfig#S3BackupConfig(String bucketName, Optional endpoint, Optional region,
   *     Optional credentials, Optional apiCallTimeout)
   */
  public S3BackupConfig(final String bucketName) {
    this(bucketName, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static S3BackupConfig from(
      final String bucketName,
      final String endpoint,
      final String region,
      final String accessKey,
      final String secretKey,
      final Duration apiCallTimeoutMs) {
    Credentials credentials = null;
    if (accessKey != null && secretKey != null) {
      credentials = new Credentials(accessKey, secretKey);
    }
    return new S3BackupConfig(
        bucketName,
        Optional.ofNullable(endpoint),
        Optional.ofNullable(region),
        Optional.ofNullable(credentials),
        Optional.ofNullable(apiCallTimeoutMs));
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
