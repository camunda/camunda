/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for an {@link AwsSecretsManagerSecretStore}.
 *
 * <p>Authentication uses the AWS SDK default credentials provider chain (e.g. IRSA/STS
 * web-identity, instance profile, environment variables, system properties). This config does not
 * accept explicit credentials fields.
 *
 * @param region the AWS region, or {@code null} to let the SDK resolve it from the environment
 *     ({@code AWS_REGION}) or instance metadata
 * @param pathPrefix optional prefix prepended to every reference name to form the AWS secret id
 *     (e.g. {@code camunda/}); {@code null} or blank means references map to bare secret names
 * @param containerSecretId opt-in: instead of one AWS secret per reference, treat every reference
 *     as a JSON key inside this one named secret. Mutually exclusive with {@code batchEnabled}
 * @param endpoint optional endpoint override, primarily for testing against LocalStack; {@code
 *     null} uses the default AWS endpoint for the resolved region
 * @param maxRetries number of retries the SDK performs on transient failures (throttling, 5xx);
 *     must be {@code >= 0}
 * @param batchEnabled opt-in: resolve via {@code BatchGetSecretValue} instead of one {@code
 *     GetSecretValue} call per reference. Off by default since it requires the {@code
 *     secretsmanager:BatchGetSecretValue} IAM action in addition to {@code GetSecretValue}.
 *     Mutually exclusive with {@code containerSecretId}
 * @param batchSize maximum secret ids per {@code BatchGetSecretValue} call when {@code
 *     batchEnabled} is set; must be between 1 and {@value #MAX_BATCH_SIZE} (AWS's hard limit)
 * @param callTimeout upper bound on one {@code resolve}/{@code list} call to AWS, retries included.
 *     Must be positive. The SDK sets no such bound by default, which lets an unresponsive endpoint
 *     hold the caller for minutes; the background resolution calls this store from an IO-bound
 *     actor thread shared with every partition's exporter, so an unbounded call there stalls
 *     exporting broker-wide (camunda/camunda#62869)
 * @param attemptTimeout upper bound on a single HTTP attempt within a call. Must be positive and
 *     not longer than {@code callTimeout}, which would make it unreachable
 */
public record AwsSecretsManagerStoreConfig(
    @Nullable String region,
    @Nullable String pathPrefix,
    @Nullable String containerSecretId,
    @Nullable URI endpoint,
    int maxRetries,
    boolean batchEnabled,
    int batchSize,
    Duration callTimeout,
    Duration attemptTimeout) {

  /** Default number of retries applied when none is configured. */
  public static final int DEFAULT_MAX_RETRIES = 3;

  /** Maximum secret ids AWS accepts in a single {@code BatchGetSecretValue} call. */
  public static final int MAX_BATCH_SIZE = 20;

  /** Default batch size when batching is enabled but none is configured. */
  public static final int DEFAULT_BATCH_SIZE = MAX_BATCH_SIZE;

  /**
   * Default upper bound on one call to AWS, retries included. Chosen so that a store that has
   * stopped answering is reported unavailable within a few resolution cycles rather than after
   * minutes: that is what lets the scheduler's retry ladder and its cooldown skip engage, which is
   * the mechanism that actually keeps an unreachable store off the IO threads.
   */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  /** Default upper bound on a single HTTP attempt within a call. */
  public static final Duration DEFAULT_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);

  // batchSize/containerSecretId invariants below are mirrored (with property-path-aware messages)
  // by io.camunda.configuration.Secrets.AwsSecretsManagerStore#validate in the configuration
  // module, which can't depend on this module's AWS SDK dependency. Keep both in sync.
  public AwsSecretsManagerStoreConfig {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative, but was " + maxRetries);
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "batchSize must be between 1 and " + MAX_BATCH_SIZE + ", but was " + batchSize);
    }
    if (containerSecretId != null && containerSecretId.isBlank()) {
      throw new IllegalArgumentException("containerSecretId must not be blank, but was empty");
    }
    if (batchEnabled && containerSecretId != null) {
      throw new IllegalArgumentException(
          "batchEnabled and containerSecretId are mutually exclusive, but both were set");
    }
    if (callTimeout == null || !callTimeout.isPositive()) {
      throw new IllegalArgumentException("callTimeout must be positive, but was " + callTimeout);
    }
    if (attemptTimeout == null || !attemptTimeout.isPositive()) {
      throw new IllegalArgumentException(
          "attemptTimeout must be positive, but was " + attemptTimeout);
    }
    if (attemptTimeout.compareTo(callTimeout) > 0) {
      throw new IllegalArgumentException(
          "attemptTimeout must not be longer than callTimeout ("
              + callTimeout
              + "), but was "
              + attemptTimeout);
    }
  }

  /**
   * Creates a config with the default timeouts. Every caller that does not tune them goes through
   * here, so a store is bounded whether or not an operator configured it.
   */
  public AwsSecretsManagerStoreConfig(
      final @Nullable String region,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretId,
      final @Nullable URI endpoint,
      final int maxRetries,
      final boolean batchEnabled,
      final int batchSize) {
    this(
        region,
        pathPrefix,
        containerSecretId,
        endpoint,
        maxRetries,
        batchEnabled,
        batchSize,
        DEFAULT_CALL_TIMEOUT,
        DEFAULT_ATTEMPT_TIMEOUT);
  }

  /**
   * Creates a config with only a path prefix, default retries, and batching disabled; region
   * resolved by the SDK.
   */
  public static AwsSecretsManagerStoreConfig of(final @Nullable String pathPrefix) {
    return new AwsSecretsManagerStoreConfig(
        null, pathPrefix, null, null, DEFAULT_MAX_RETRIES, false, DEFAULT_BATCH_SIZE);
  }
}
