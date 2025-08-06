package io.camunda.zeebe.broker.clustering.mapper;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class S3LeaseConfig {
  private String bucketName;
  private Optional<String> endpoint = Optional.empty();
  private Optional<String> region = Optional.empty();
  private String accessKey;
  private String secretKey;
  private Optional<Duration> apiCallTimeout = Optional.empty();
  private Optional<String> basePath = Optional.empty();
  private Duration connectionAcquisitionTimeout = Duration.ofSeconds(45);

  public S3LeaseConfig() {}

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public Optional<String> getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final Optional<String> endpoint) {
    this.endpoint = endpoint;
  }

  public Optional<String> getRegion() {
    return region;
  }

  public void setRegion(final Optional<String> region) {
    this.region = region;
  }

  public Optional<Duration> getApiCallTimeout() {
    return apiCallTimeout;
  }

  public void setApiCallTimeout(final Optional<Duration> apiCallTimeout) {
    this.apiCallTimeout = apiCallTimeout;
  }

  public Optional<String> getBasePath() {
    return basePath;
  }

  public void setBasePath(final Optional<String> basePath) {
    this.basePath = basePath;
  }

  public Duration getConnectionAcquisitionTimeout() {
    return connectionAcquisitionTimeout;
  }

  public void setConnectionAcquisitionTimeout(final Duration connectionAcquisitionTimeout) {
    this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
  }

  public String bucketName() {
    return bucketName;
  }

  public Optional<String> endpoint() {
    return endpoint;
  }

  public Optional<String> region() {
    return region;
  }

  public Optional<Duration> apiCallTimeout() {
    return apiCallTimeout;
  }

  public Optional<String> basePath() {
    return basePath;
  }

  public Duration connectionAcquisitionTimeout() {
    return connectionAcquisitionTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        bucketName, endpoint, region, apiCallTimeout, basePath, connectionAcquisitionTimeout);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (S3LeaseConfig) obj;
    return Objects.equals(bucketName, that.bucketName)
        && Objects.equals(endpoint, that.endpoint)
        && Objects.equals(region, that.region)
        && Objects.equals(apiCallTimeout, that.apiCallTimeout)
        && Objects.equals(basePath, that.basePath)
        && Objects.equals(connectionAcquisitionTimeout, that.connectionAcquisitionTimeout);
  }

  @Override
  public String toString() {
    return "S3LeaseConfig["
        + "bucketName="
        + bucketName
        + ", "
        + "endpoint="
        + endpoint
        + ", "
        + "region="
        + region
        + ", "
        + "apiCallTimeout="
        + apiCallTimeout
        + ", "
        + "basePath="
        + basePath
        + ", "
        + "connectionAcquisitionTimeout="
        + connectionAcquisitionTimeout
        + ']';
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
}
