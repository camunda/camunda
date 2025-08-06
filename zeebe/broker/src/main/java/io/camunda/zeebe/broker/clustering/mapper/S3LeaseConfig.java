package io.camunda.zeebe.broker.clustering.mapper;

import java.time.Duration;
import java.util.Optional;

public record S3LeaseConfig(
    String bucketName,
    Optional<String> endpoint,
    Optional<String> region,
    Optional<Credentials> credentials,
    Optional<Duration> apiCallTimeout,
    Optional<String> basePath,
    Duration connectionAcquisitionTimeout) {

  public S3LeaseConfig {
    if (bucketName == null || bucketName.isEmpty()) {
      throw new IllegalArgumentException("Bucket name must not be empty.");
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
    private Credentials credentials;
    private String basePath;

    /** Default from `SdkHttpConfigurationOption.DEFAULT_CONNECTION_ACQUIRE_TIMEOUT` */
    private final Duration connectionAcquisitionTimeout = Duration.ofSeconds(45);

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

    public Builder withBasePath(final String basePath) {
      this.basePath = basePath;
      return this;
    }

    public S3LeaseConfig build() {
      return new S3LeaseConfig(
          bucketName,
          Optional.ofNullable(endpoint),
          Optional.ofNullable(region),
          Optional.ofNullable(credentials),
          Optional.ofNullable(apiCallTimeoutMs),
          Optional.ofNullable(basePath),
          connectionAcquisitionTimeout);
    }
  }

  public record Credentials(String accessKey, String secretKey) {
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
