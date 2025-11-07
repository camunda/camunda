/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

//  @ConfigurationProperties
public class DynamicNodeIdConfig {
  private Type type = Type.NONE;
  private @NestedConfigurationProperty S3 s3 = new S3();

  public Type getType() {
    return type;
  }

  public void setType(final Type type) {
    this.type = type;
  }

  /**
   * Returns the S3 configuration without validation. This method is intended for internal use by
   * Spring Boot property binding. For application code, use {@link #s3()} instead, which validates
   * that the type is correctly set to S3.
   */
  S3 getS3() {
    return s3;
  }

  public void setS3(final S3 s3) {
    this.s3 = s3;
  }

  /**
   * Returns the S3 configuration with validation. This method ensures that the dynamic node ID type
   * is set to S3 before accessing the S3 configuration.
   *
   * @return the S3 configuration object
   * @throws IllegalStateException if the type is not S3
   */
  public S3 s3() {
    if (type != Type.S3) {
      throw new IllegalStateException(
          "Cannot access S3 configuration when dynamic node ID type is "
              + type
              + ". Use s3() method to access S3 configuration, which validates the type.");
    }
    return s3;
  }

  public enum Type {
    NONE,
    S3;
  }

  public class S3 {

    /** Lease duration before expiry */
    private Duration leaseDuration;

    /** The taskId to use when registering to a lease * */
    private String taskId;

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
     * Configure access credentials. If either accessKey or secretKey is not provided, the
     * credentials will be determined as documented in <a
     * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
     */
    private String accessKey;

    /**
     * Configure access credentials. If either accessKey or secretKey is not provided, the
     * credentials will be determined as documented in <a
     * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
     */
    private String secretKey;

    /**
     * Configure a maximum duration for all S3 client API calls. Lower values will ensure that
     * failed or slow API calls don't block other backups but may increase the risk that backups
     * can't be stored if uploading parts of the backup takes longer than the configured timeout.
     * See <a
     * href="https://github.com/aws/aws-sdk-java-v2/blob/master/docs/BestPractices.md#utilize-timeout-configurations">...</a>
     */
    private final Duration apiCallTimeout = Duration.ofSeconds(180);

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

    public String getBucketName() {
      return bucketName;
    }

    public void setBucketName(final String bucketName) {
      this.bucketName = bucketName;
    }

    @Override
    public String toString() {
      return "S3{"
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
          + secretKey
          + '\''
          + ", apiCallTimeout="
          + apiCallTimeout
          + '}';
    }

    public Duration getLeaseDuration() {
      return leaseDuration;
    }

    public void setLeaseDuration(final Duration leaseDuration) {
      this.leaseDuration = leaseDuration;
    }

    public String getTaskId() {
      return taskId;
    }

    public void setTaskId(final String taskId) {
      this.taskId = taskId;
    }
  }
}
