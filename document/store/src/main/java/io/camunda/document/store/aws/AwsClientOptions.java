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

  public static AwsClientOptions sdkDefaults() {
    return new AwsClientOptions(null, null, null, null, null, null, null);
  }

  /** Whether this store authenticates with its own key pair instead of the process credentials. */
  public boolean hasStaticCredentials() {
    return accessKey != null && secretKey != null;
  }

  /**
   * Whether every setting that addresses the backing store is left to the AWS SDK — no endpoint,
   * region, path-style or chunked-encoding override, and no key pair of this store's own.
   *
   * <p>When true the caller must return the plain {@code S3Client.create()} / {@code
   * S3Presigner.create()} instead of configuring a builder, so the SDK resolves all of it from the
   * process environment as it did before per-store clients existed. Kept here, as one predicate
   * both builders share, so the two cannot drift apart: a setting added to the record but forgotten
   * in one of the two lists would silently strand that builder on the wrong path.
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

  /** Renders the options with the secret key masked, so they are safe to log. */
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
        + accessKey
        + ", secretKey="
        + (secretKey == null ? null : "<redacted>")
        + "]";
  }
}
