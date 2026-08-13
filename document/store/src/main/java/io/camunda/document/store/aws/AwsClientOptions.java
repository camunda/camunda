/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Per-store overrides applied to the S3 client and presigner of a single AWS document store. Every
 * component is optional: a {@code null} means "leave it to the AWS SDK", which resolves it from the
 * process environment.
 */
@NullMarked
public record AwsClientOptions(
    @Nullable URI endpointOverride,
    @Nullable Boolean forcePathStyle,
    @Nullable Boolean chunkedEncodingEnabled,
    @Nullable Boolean supportLegacyMd5,
    @Nullable String region,
    @Nullable String accessKey,
    @Nullable String secretKey) {

  private static final String REDACTED = "<redacted>";

  /**
   * Guards the one combination that fails silently instead of loudly: with only one half of the key
   * pair {@link #hasStaticCredentials()} is false, so the store keeps the AWS SDK default chain —
   * the process-wide credentials it was configured to stop using. Callers reaching this record
   * through {@link AwsDocumentStoreProvider} are already rejected there with a message naming the
   * store and the offending properties; this covers whoever constructs it directly.
   */
  public AwsClientOptions {
    if ((accessKey == null) != (secretKey == null)) {
      throw new IllegalArgumentException(
          "accessKey and secretKey must be set together: with only one of them the store"
              + " authenticates with the AWS SDK default credential chain, which is shared with"
              + " every other store in the process.");
    }
  }

  public static AwsClientOptions sdkDefaults() {
    return new AwsClientOptions(null, null, null, null, null, null, null);
  }

  /** Whether this store authenticates with its own key pair instead of the process credentials. */
  public boolean hasStaticCredentials() {
    return accessKey != null && secretKey != null;
  }

  /**
   * Whether the caller must return the plain {@code S3Client.create()} / {@code
   * S3Presigner.create()} rather than configure a builder, leaving the SDK to resolve everything
   * from the process environment as it did before per-store clients existed.
   *
   * <p>Shared by both builders so they cannot drift: a component added to the record but forgotten
   * in one of two hand-maintained conditions would silently strand that builder on the wrong path.
   */
  public boolean usesSdkDefaults() {
    return endpointOverride == null
        && forcePathStyle == null
        && chunkedEncodingEnabled == null
        && region == null
        && !hasStaticCredentials();
  }

  /**
   * The credentials provider for this store, or {@code null} when it has no key pair of its own —
   * in which case the caller must leave the builder untouched so the SDK applies its default
   * credentials chain.
   */
  public @Nullable AwsCredentialsProvider credentialsProvider() {
    return hasStaticCredentials()
        ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        : null;
  }

  /**
   * Renders the options safely to log. The access key is masked alongside the secret because it
   * names the IAM principal the store acts as — what a reader of the log would need to know which
   * secret is worth hunting for.
   */
  @Override
  public String toString() {
    return "AwsClientOptions[endpointOverride="
        + endpointOverride
        + ", forcePathStyle="
        + forcePathStyle
        + ", chunkedEncodingEnabled="
        + chunkedEncodingEnabled
        + ", supportLegacyMd5="
        + supportLegacyMd5
        + ", region="
        + region
        + ", accessKey="
        + (accessKey == null ? null : REDACTED)
        + ", secretKey="
        + (secretKey == null ? null : REDACTED)
        + "]";
  }
}
