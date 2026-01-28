/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NodeIdProvider {

  /**
   * Set the {@link Type} of the implementation for the provider of dynamic node id. {@link
   * Type#FIXED} refers to no provider.
   */
  private Type type = Type.FIXED;

  /** Configuration to use S3 for dynamic node id. */
  private S3 s3 = new S3();

  /**
   * This field is bound to "static" as property name. Unfortunately it cannot be called static
   * because it's a reserved keyword in java. However, the getter is called `getStatic`, so this
   * field is bound as if it was name `static`.
   */
  private final Fixed fixed = new Fixed();

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
    assertType(Type.S3);
    return s3;
  }

  public Fixed fixed() {
    assertType(Type.FIXED);
    return fixed;
  }

  /**
   * Getter for spring configuration binding, do not use it, as it does not check the type. Note
   * that it's called `getStatic` and not `getStaticConfig` since we want to bind it to the property
   * "static", not "static-config"
   */
  Fixed getFixed() {
    return fixed;
  }

  private void assertType(final Type expected) {
    if (type != expected) {
      throw new IllegalStateException(
          "Cannot access "
              + expected
              + " configuration in NodeIdProvider configuration, type is "
              + type
              + ".");
    }
  }

  public static class S3 {

    /** Lease duration before expiry */
    private Duration leaseDuration;

    /** Maximum delay for exponential backoff when retrying failed lease renewals. */
    private Duration leaseAcquireMaxDelay = Duration.ofSeconds(30);

    /**
     * Timeout for waiting until node ID version mappings is updated. If a node's mapping is not
     * updated within this timeout, the node is assumed to be down and its mapping may be ignored.
     * Must be greater than leaseDuration.
     */
    private Duration readinessCheckTimeout = Duration.ofMinutes(2);

    /**
     * Name of the bucket where the leases will be stored. The bucket must be already created. The
     * bucket must not be shared with other zeebe clusters. bucketName must not be empty.
     */
    private String bucketName;

    /** The taskId to use when registering to a lease * */
    private Optional<String> taskId = Optional.empty();

    /**
     * Configure URL endpoint for the store. If no endpoint is provided, it will be determined based
     * on the configured region.
     */
    private Optional<String> endpoint = Optional.empty();

    /**
     * Configure AWS region. If no region is provided it will be determined as documented in <a
     * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment">...</a>
     */
    private Optional<String> region = Optional.empty();

    /**
     * Configure access credentials. If either accessKey or secretKey is not provided, the
     * credentials will be determined as documented in <a
     * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
     */
    private Optional<String> accessKey = Optional.empty();

    /**
     * Configure access credentials. If either accessKey or secretKey is not provided, the
     * credentials will be determined as documented in <a
     * href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">...</a>
     */
    private Optional<String> secretKey = Optional.empty();

    /**
     * Configure a maximum duration for all S3 client API calls. See <a
     * href="https://github.com/aws/aws-sdk-java-v2/blob/master/docs/BestPractices.md#utilize-timeout-configurations">...</a>
     */
    private Duration apiCallTimeout = Duration.ofSeconds(10);

    public Optional<String> getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final String endpoint) {
      this.endpoint = Optional.ofNullable(endpoint);
    }

    public Optional<String> getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = Optional.ofNullable(region);
    }

    public Optional<String> getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(final String accessKey) {
      this.accessKey = Optional.ofNullable(accessKey);
    }

    public Optional<String> getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(final String secretKey) {
      this.secretKey = Optional.ofNullable(secretKey);
    }

    public Duration getApiCallTimeout() {
      return apiCallTimeout;
    }

    public void setApiCallTimeout(final Duration apiCallTimeout) {
      this.apiCallTimeout = apiCallTimeout;
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
      this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration cannot be null");
    }

    public Optional<String> getTaskId() {
      return taskId;
    }

    public void setTaskId(final String taskId) {
      this.taskId = Optional.ofNullable(taskId);
    }

    public Duration getLeaseAcquireMaxDelay() {
      return leaseAcquireMaxDelay;
    }

    public void setLeaseAcquireMaxDelay(final Duration leaseAcquireMaxDelay) {
      this.leaseAcquireMaxDelay = leaseAcquireMaxDelay;
    }

    public Duration getReadinessCheckTimeout() {
      return readinessCheckTimeout;
    }

    public void setReadinessCheckTimeout(final Duration readinessCheckTimeout) {
      this.readinessCheckTimeout =
          Objects.requireNonNull(readinessCheckTimeout, "readinessCheckTimeout cannot be null");
    }
  }

  public static final class Fixed {
    private int nodeId;

    public int getNodeId() {
      return UnifiedConfigurationHelper.validateLegacyConfiguration(
          Cluster.PREFIX + ".node-id",
          nodeId,
          Integer.class,
          UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
          Set.of(Cluster.LEGACY_NODE_ID_PROPERTY));
    }

    public void setNodeId(final int nodeId) {
      this.nodeId = nodeId;
    }

    @Override
    public String toString() {
      return "StaticConfig{" + "nodeId=" + nodeId + '}';
    }
  }

  public enum Type {
    FIXED,
    S3;
  }
}
