/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Per-store overrides applied to the S3 client and presigner of a single AWS document store. Every
 * component is optional: a {@code null} means "leave it to the AWS SDK", which resolves it from the
 * process environment.
 */
public record AwsClientOptions(
    URI endpointOverride,
    Boolean forcePathStyle,
    Boolean chunkedEncodingEnabled,
    Boolean supportLegacyMd5,
    String region,
    String accessKey,
    String secretKey) {

  public static AwsClientOptions sdkDefaults() {
    return new AwsClientOptions(null, null, null, null, null, null, null);
  }

  /** Whether this store authenticates with its own key pair instead of the process credentials. */
  public boolean hasStaticCredentials() {
    return accessKey != null && secretKey != null;
  }

  /**
   * The credentials provider for this store, or {@code null} when it has no key pair of its own —
   * in which case the caller must leave the builder untouched so the SDK applies its default
   * credentials chain.
   */
  public AwsCredentialsProvider credentialsProvider() {
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
