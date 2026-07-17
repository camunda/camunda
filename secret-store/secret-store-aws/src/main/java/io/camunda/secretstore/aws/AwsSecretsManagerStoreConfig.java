/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for an {@link AwsSecretsManagerSecretStore}.
 *
 * <p>Authentication is always identity-based: the AWS SDK default credentials provider chain is
 * used (IRSA/STS web-identity, instance profile, environment). No static credentials are accepted
 * here by design.
 *
 * @param region the AWS region, or {@code null} to let the SDK resolve it from the environment
 *     ({@code AWS_REGION}) or instance metadata
 * @param pathPrefix optional prefix prepended to every reference name to form the AWS secret id
 *     (e.g. {@code camunda/}); {@code null} or blank means references map to bare secret names
 * @param endpoint optional endpoint override, primarily for testing against LocalStack; {@code
 *     null} uses the default AWS endpoint for the resolved region
 * @param maxRetries number of retries the SDK performs on transient failures (throttling, 5xx);
 *     must be {@code >= 0}
 * @param containerSecretId opt-in: when set, every reference is treated as a JSON key inside this
 *     one named secret instead of its own AWS secret; {@code null} keeps the default one-secret
 *     per-reference behavior
 */
public record AwsSecretsManagerStoreConfig(
    @Nullable String region,
    @Nullable String pathPrefix,
    @Nullable URI endpoint,
    int maxRetries,
    @Nullable String containerSecretId) {

  /** Default number of retries applied when none is configured. */
  public static final int DEFAULT_MAX_RETRIES = 3;

  public AwsSecretsManagerStoreConfig {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative, but was " + maxRetries);
    }
  }

  /** Creates a config with only a path prefix and default retries; region resolved by the SDK. */
  public static AwsSecretsManagerStoreConfig of(final @Nullable String pathPrefix) {
    return new AwsSecretsManagerStoreConfig(null, pathPrefix, null, DEFAULT_MAX_RETRIES, null);
  }
}
